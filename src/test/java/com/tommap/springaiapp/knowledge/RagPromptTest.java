package com.tommap.springaiapp.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;

class RagPromptTest {

    private static String wrapped(String question, String context) {
        return new PromptTemplate(RagPrompt.TEMPLATE)
                .render(Map.of("query", question, "question_answer_context", context));
    }

    @Test
    void theWrappedMessageCanBeTakenApartAgain() {
        String message = wrapped("How long does honey kefir keep?", "Honey kefir keeps 3 days.\nSealed.");

        assertThat(RagPrompt.questionOf(message)).isEqualTo("How long does honey kefir keep?");
        assertThat(RagPrompt.contextOf(message)).contains("Honey kefir keeps 3 days.\nSealed.");
    }

    @Test
    void anEmptyRetrievalLeavesAnEmptyContext() {
        assertThat(RagPrompt.contextOf(wrapped("Do you deliver to Hanoi?", ""))).contains("");
    }

    @Test
    void aMessageThatWasNeverWrappedHasNoContext() {
        assertThat(RagPrompt.questionOf("hello")).isEqualTo("hello");
        assertThat(RagPrompt.contextOf("hello")).isEmpty();
    }

    @Test
    void theInstructionsKeepToolsAvailableAndAdmitMissingKnowledge() {
        String message = wrapped("order 2 honey kefir", "");

        assertThat(message).contains("never let it stop you from using your tools")
                .contains("say you don't know");
    }
}
