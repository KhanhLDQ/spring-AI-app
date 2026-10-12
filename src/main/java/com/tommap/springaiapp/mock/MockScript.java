package com.tommap.springaiapp.mock;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Scripted answers keyed by what the user says, including the tool calls a real model would choose. A scripted
 * answer follows the language of the question, like the real model; extended as features that need scripted
 * behaviour (RAG) land.
 */
final class MockScript {

    /** Either text for the user or tool calls for the framework to execute, never both. */
    record Reply(String text, List<ToolCall> toolCalls) {

        static Reply text(String text) {
            return new Reply(text, List.of());
        }

        static Reply tool(String name, String arguments) {
            return new Reply("", List.of(new ToolCall("mock-" + name, "function", name, arguments)));
        }

        boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String FALLBACK = "This is a simulated answer (mock mode, no real model call).";
    private static final Pattern CANCEL = Pattern.compile("(?:cancel|huỷ|hủy)\\s+(?:order|đơn)?\\s*#?(\\d+)");
    private static final Pattern CHANGE = Pattern.compile("(?:change|update)\\s+order\\s+#?(\\d+)\\s+to\\s+(\\d+)");
    private static final Pattern ORDER = Pattern.compile("(?:order|đặt)\\s+(\\d+)\\s+(?:chai\\s+)?(.+)");
    private static final Pattern GREETING_EN = Pattern.compile("\\b(hello|hi)\\b");
    private static final Map<String, String> SKU_BY_KEYWORD = Map.of(
            "honey", "MAT-ONG-330", "mật ong", "MAT-ONG-330",
            "original", "NGUYEN-BAN-330", "nguyên bản", "NGUYEN-BAN-330",
            "greek", "HY-LAP-250", "hy lạp", "HY-LAP-250",
            "sesame", "ME-DEN-330", "mè đen", "ME-DEN-330");

    private MockScript() {
    }

    static Reply replyTo(List<Message> conversation) {
        Message last = conversation.getLast();
        if (last instanceof ToolResponseMessage toolResults) {
            return Reply.text(afterTools(toolResults));
        }
        return reply(lastUserText(conversation));
    }

    private static String lastUserText(List<Message> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i).getMessageType() == MessageType.USER) {
                return conversation.get(i).getText();
            }
        }
        return "";
    }

    private static Reply reply(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        Matcher cancel = CANCEL.matcher(q);
        if (cancel.find()) {
            return Reply.tool("cancelOrder", "{\"orderId\":" + cancel.group(1) + "}");
        }
        Matcher change = CHANGE.matcher(q);
        if (change.find()) {
            return Reply.tool("updateOrderQuantity",
                    "{\"orderId\":" + change.group(1) + ",\"newQuantity\":" + change.group(2) + "}");
        }
        Matcher order = ORDER.matcher(q);
        if (order.find()) {
            String sku = skuFor(order.group(2));
            if (sku != null) {
                return Reply.tool("createOrder", "{\"product\":\"" + sku + "\",\"quantity\":" + order.group(1) + "}");
            }
        }
        if (q.contains("my orders") || q.contains("đơn của tôi")) {
            return Reply.tool("listMyOrders", "{}");
        }
        if (q.contains("all orders") || q.contains("revenue") || q.contains("doanh thu")) {
            return Reply.tool("listAllOrders", "{}");
        }
        if (q.contains("products") || q.contains("menu") || q.contains("sản phẩm")) {
            return Reply.tool("listProducts", "{}");
        }
        if (q.contains("xin chào")) {
            return Reply.text("Xin chào! Mình là trợ lý Kefir (chế độ mock). Bạn muốn hỏi về sản phẩm hay đặt hàng?");
        }
        if (GREETING_EN.matcher(q).find()) {
            return Reply.text("Hello! I'm the Kefir assistant (mock mode). Ask me about products or place an order.");
        }
        return Reply.text(FALLBACK);
    }

    private static String skuFor(String productWords) {
        String sku = SKU_BY_KEYWORD.entrySet().stream()
                .filter(e -> productWords.contains(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
        return sku != null && productWords.contains("500") ? sku.replace("330", "500") : sku;
    }

    /** Plays the model's second turn: relay a refusal as is, or sum up a successful result. */
    private static String afterTools(ToolResponseMessage results) {
        ToolResponseMessage.ToolResponse result = results.getResponses().getFirst();
        JsonNode data = parse(result.responseData());
        if (data == null || !(data.isObject() || data.isArray())) {
            return "Sorry, I couldn't do that. " + result.responseData();
        }
        if (data.isArray()) {
            return "I found " + data.size() + " item(s). Details are in the Inspector.";
        }
        if (data.has("status")) {
            return "Done. Order #" + data.path("id").asLong() + " is " + data.path("status").asString()
                    + ", total " + data.path("totalAmount").asLong() + " VND.";
        }
        return "Here is what I found: " + data;
    }

    private static JsonNode parse(String text) {
        try {
            return JSON.readTree(text);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
