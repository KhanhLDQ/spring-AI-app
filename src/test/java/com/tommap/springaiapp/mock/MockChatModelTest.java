package com.tommap.springaiapp.mock;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

class MockChatModelTest {

    private final MockChatModel model = new MockChatModel(ObservationRegistry.NOOP);

    @Test
    void callReturnsScriptedReplyWithUsage() {
        ChatResponse response = model.call(new Prompt("xin chào"));

        assertThat(response.getResult().getOutput().getText()).contains("trợ lý Kefir");
        assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
        assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
    }

    @Test
    void streamSplitsReplyIntoChunksThatJoinToTheFullReply() {
        List<ChatResponse> chunks = model.stream(new Prompt("xin chào")).collectList().block();

        assertThat(chunks).hasSizeGreaterThan(1);
        String joined = chunks.stream().map(c -> c.getResult().getOutput().getText()).reduce("", String::concat);
        assertThat(joined).isEqualTo(model.call(new Prompt("xin chào")).getResult().getOutput().getText());
    }
}
