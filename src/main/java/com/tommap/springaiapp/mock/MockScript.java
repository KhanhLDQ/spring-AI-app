package com.tommap.springaiapp.mock;

/**
 * Canned answers keyed by topic; extended as features that need scripted behaviour (tools, RAG) land.
 * Like the real model, a scripted answer follows the language of the question.
 */
final class MockScript {

    private static final String FALLBACK = "This is a simulated answer (mock mode, no real model call).";

    private MockScript() {
    }

    static String replyTo(String question) {
        String q = question == null ? "" : question.toLowerCase();
        if (q.contains("xin chào")) {
            return "Xin chào! Mình là trợ lý Kefir (chế độ mock). Bạn muốn hỏi về sản phẩm hay đặt hàng?";
        }
        if (q.contains("hello") || q.contains("hi ")) {
            return "Hello! I'm the Kefir assistant (mock mode). Ask me about products or place an order.";
        }
        return FALLBACK;
    }
}
