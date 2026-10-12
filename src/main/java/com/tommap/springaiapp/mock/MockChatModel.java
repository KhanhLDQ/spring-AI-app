package com.tommap.springaiapp.mock;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

/** Scripted stand-in for the real model: no network, no key. Emits the same model observation as a real provider. */
public class MockChatModel implements ChatModel {

    static final String MODEL_NAME = "mock";
    private static final String OBSERVATION_NAME = "gen_ai.client.operation";
    private static final Duration CHUNK_DELAY = Duration.ofMillis(40);

    private final ObservationRegistry observationRegistry;

    public MockChatModel(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    /** Tool-calling options, like a real provider's, so the ChatClient can attach tool callbacks to requests. */
    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatModelObservationContext context = context(prompt, false);
        Observation observation = observation(context).start();
        try (Observation.Scope ignored = observation.openScope()) {
            ChatResponse response = response(prompt, reply(prompt));
            context.setResponse(response);
            return response;
        } finally {
            observation.stop();
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.deferContextual(context -> {
            ChatModelObservationContext modelContext = context(prompt, true);
            Observation observation = observation(modelContext)
                    .parentObservation(context.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
                    .start();
            MockScript.Reply reply = reply(prompt);
            ChatResponse full = response(prompt, reply);
            Flux<ChatResponse> chunks = reply.hasToolCalls() ? Flux.just(full) : textChunks(reply.text());
            return chunks
                    .doOnComplete(() -> {
                        modelContext.setResponse(full);
                        observation.stop();
                    })
                    .doOnError(observation::error);
        });
    }

    private static Flux<ChatResponse> textChunks(String text) {
        return Flux.fromArray(text.split("(?<=\\s)"))
                .delayElements(CHUNK_DELAY)
                .map(chunk -> new ChatResponse(List.of(new Generation(new AssistantMessage(chunk)))));
    }

    private ChatModelObservationContext context(Prompt prompt, boolean streaming) {
        return ChatModelObservationContext.builder()
                .prompt(prompt).provider(MODEL_NAME).streaming(streaming).build();
    }

    private Observation observation(ChatModelObservationContext context) {
        return Observation.createNotStarted(OBSERVATION_NAME, () -> context, observationRegistry);
    }

    private ChatResponse response(Prompt prompt, MockScript.Reply reply) {
        int in = MockTokens.estimate(prompt.getContents());
        int out = MockTokens.estimate(reply.text() + reply.toolCalls());
        AssistantMessage message = AssistantMessage.builder().content(reply.text()).toolCalls(reply.toolCalls()).build();
        return ChatResponse.builder()
                .generations(List.of(new Generation(message, finishMetadata(reply))))
                .metadata(ChatResponseMetadata.builder().model(MODEL_NAME).usage(new DefaultUsage(in, out)).build())
                .build();
    }

    private static ChatGenerationMetadata finishMetadata(MockScript.Reply reply) {
        return ChatGenerationMetadata.builder().finishReason(reply.hasToolCalls() ? "tool_use" : "end_turn").build();
    }

    private MockScript.Reply reply(Prompt prompt) {
        return MockScript.replyTo(prompt.getInstructions());
    }
}
