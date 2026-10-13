package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class RoundBreaksTest {

    private final AtomicInteger toolsRun = new AtomicInteger();
    private final ChatService.RoundBreaks breaks = new ChatService.RoundBreaks(toolsRun::get);

    private static ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void textBeforeAndAfterAToolIsSeparatedByABlankLine() {
        assertThat(breaks.next(text("Placing it "))).isEqualTo("Placing it ");
        assertThat(breaks.next(text("now."))).isEqualTo("now.");

        toolsRun.incrementAndGet();

        assertThat(breaks.next(text("Done!"))).isEqualTo("\n\nDone!");
        assertThat(breaks.next(text(" Order #1."))).isEqualTo(" Order #1.");
    }

    @Test
    void noBreakWhenTheModelSaidNothingBeforeTheTool() {
        toolsRun.incrementAndGet();

        assertThat(breaks.next(text("Done!"))).isEqualTo("Done!");
    }

    @Test
    void emptyChunksAreDropped() {
        assertThat(breaks.next(text(""))).isNull();
    }
}
