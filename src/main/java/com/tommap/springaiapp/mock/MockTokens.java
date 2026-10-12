package com.tommap.springaiapp.mock;

final class MockTokens {

    private MockTokens() {
    }

    /** Rough estimate (~1.3 tokens per word) so the Inspector shows plausible numbers. */
    static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.trim().split("\\s+").length * 1.3);
    }
}
