package com.tommap.springaiapp.knowledge;

import java.util.Optional;

/**
 * Wraps the user's question with the retrieved chunks. The router skips clear order requests, but an unclear or
 * mixed message is still retrieved for, so the text tells the model to ignore irrelevant context and keep using its tools.
 * The helpers read the wrapped message back, so the scripted mock model stays in step with this format.
 */
public final class RagPrompt {

    private static final String INTRO = "Shop knowledge retrieved for this message (may be empty or unrelated):";
    private static final String CONTEXT_START = "<knowledge>";
    private static final String CONTEXT_END = "</knowledge>";

    public static final String TEMPLATE = """
            {query}

            %s
            %s
            {question_answer_context}
            %s
            Use it for questions about the shop's products, prices, shelf life, policies and how to drink kefir, \
            and prefer it over your own knowledge. Ignore it when it is not relevant, and never let it stop you \
            from using your tools. If the user needs shop information that is not in the knowledge, say you don't know.\
            """.formatted(INTRO, CONTEXT_START, CONTEXT_END);

    private RagPrompt() {
    }

    /** The user's own words, without the retrieval wrapper. */
    public static String questionOf(String message) {
        int intro = message.indexOf("\n\n" + INTRO);
        return intro < 0 ? message : message.substring(0, intro);
    }

    /** The retrieved chunks (possibly empty), or empty when the message was not wrapped at all. */
    public static Optional<String> contextOf(String message) {
        int start = message.indexOf(CONTEXT_START);
        int end = message.indexOf(CONTEXT_END);
        if (start < 0 || end < start) {
            return Optional.empty();
        }
        return Optional.of(message.substring(start + CONTEXT_START.length(), end).strip());
    }
}
