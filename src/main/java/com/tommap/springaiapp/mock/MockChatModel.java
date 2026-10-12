package com.tommap.springaiapp.mock;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
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
            String reply = reply(prompt);
            List<String> chunks = Arrays.stream(reply.split("(?<=\\s)")).toList();
            ChatResponse full = response(prompt, reply);
            return Flux.fromIterable(chunks)
                    .delayElements(CHUNK_DELAY)
                    .map(chunk -> new ChatResponse(List.of(new Generation(new AssistantMessage(chunk)))))
                    .doOnComplete(() -> {
                        modelContext.setResponse(full);
                        observation.stop();
                    })
                    .doOnError(observation::error);
        });
    }

    private ChatModelObservationContext context(Prompt prompt, boolean streaming) {
        return ChatModelObservationContext.builder()
                .prompt(prompt).provider(MODEL_NAME).streaming(streaming).build();
    }

    private Observation observation(ChatModelObservationContext context) {
        return Observation.createNotStarted(OBSERVATION_NAME, () -> context, observationRegistry);
    }

    private ChatResponse response(Prompt prompt, String reply) {
        int in = MockTokens.estimate(prompt.getContents());
        int out = MockTokens.estimate(reply);
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(reply))))
                .metadata(ChatResponseMetadata.builder().model(MODEL_NAME).usage(new DefaultUsage(in, out)).build())
                .build();
    }

    private String reply(Prompt prompt) {
        return MockScript.replyTo(prompt.getUserMessage().getText());
    }
}
