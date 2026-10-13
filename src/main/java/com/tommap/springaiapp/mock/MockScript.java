package com.tommap.springaiapp.mock;

import com.tommap.springaiapp.knowledge.RagPrompt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 * answer follows the language of the question, like the real model. Knowledge answers are extractive: the best
 * matching line of the retrieved context, or "I don't know" when retrieval found nothing.
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
    private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");
    private static final String FALLBACK = "This is a simulated answer (mock mode, no real model call).";
    private static final Pattern CANCEL = Pattern.compile("(?:cancel|huỷ|hủy)\\s+(?:order|đơn)?\\s*#?(\\d+)");
    private static final Pattern CHANGE = Pattern.compile("(?:change|update)\\s+order\\s+#?(\\d+)\\s+to\\s+(\\d+)");
    private static final Pattern FOLLOW_UP = Pattern.compile(
            "(?:(?:make it|change it to|đổi (?:nó )?thành)\\s+(\\d+))|(?:cancel it|huỷ nó|hủy nó|huỷ đơn đó|hủy đơn đó)");
    private static final Pattern ORDER_ID = Pattern.compile("Order #(\\d+)");
    private static final Pattern NAME_GIVEN = Pattern.compile(
            "(?:my name is|tên mình là|tên tôi là|mình tên là|tôi tên là)\\s+(\\p{L}+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_ASKED = Pattern.compile(
            "what(?:'s| is) my name|tên (?:mình|tôi) là gì|(?:mình|tôi) tên (?:là )?gì");
    private static final Pattern ORDER = Pattern.compile("(?:order|đặt)\\s+(\\d+)\\s+(?:chai\\s+)?(.+)");
    private static final Pattern PRICE = Pattern.compile("(?:price|how much|giá)\\s+(?:of\\s+|is\\s+)?(.+)");
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
        String message = lastUserText(conversation);
        return reply(RagPrompt.questionOf(message), RagPrompt.contextOf(message),
                conversation.subList(0, conversation.size() - 1));
    }

    private static String lastUserText(List<Message> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i).getMessageType() == MessageType.USER) {
                return conversation.get(i).getText();
            }
        }
        return "";
    }

    private static Reply reply(String question, Optional<String> context, List<Message> history) {
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
        Matcher follow = FOLLOW_UP.matcher(q);
        if (follow.find()) {
            return followUp(follow, history, question);
        }
        Matcher nameGiven = NAME_GIVEN.matcher(question);
        if (nameGiven.find()) {
            String name = nameGiven.group(1);
            return Reply.text(NON_ASCII.matcher(question).find() || q.contains("tên")
                    ? "Rất vui được gặp bạn, " + name + "!" : "Nice to meet you, " + name + "!");
        }
        if (NAME_ASKED.matcher(q).find()) {
            return nameFrom(history).map(name -> Reply.text(vietnamese(question)
                            ? "Tên bạn là " + name + "." : "Your name is " + name + "."))
                    .orElse(Reply.text(vietnamese(question)
                            ? "Mình chưa biết tên bạn." : "I don't know your name yet."));
        }
        Matcher order = ORDER.matcher(q);
        if (order.find()) {
            String sku = skuFor(order.group(2));
            if (sku != null) {
                return Reply.tool("createOrder", "{\"product\":\"" + sku + "\",\"quantity\":" + order.group(1) + "}");
            }
        }
        Matcher price = PRICE.matcher(q);
        if (price.find() && skuFor(price.group(1)) != null) {
            return Reply.tool("getProduct", "{\"product\":\"" + skuFor(price.group(1)) + "\"}");
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
        return context.map(chunks -> Reply.text(answerFrom(chunks, question))).orElseGet(() -> Reply.text(FALLBACK));
    }

    /** Quotes the two context paragraphs that share the most words with the question, like a very literal reader. */
    private static String answerFrom(String context, String question) {
        List<String> questionWords = words(question);
        List<String> best = paragraphs(context).stream()
                .filter(paragraph -> overlap(paragraph, questionWords) > 0)
                .sorted(Comparator.comparingLong((String paragraph) -> overlap(paragraph, questionWords)).reversed())
                .limit(2).toList();
        if (!best.isEmpty()) {
            return String.join("\n\n", best);
        }
        return vietnamese(question)
                ? "Xin lỗi, mình không có thông tin về điều này."
                : "Sorry, I don't have that information.";
    }

    /** Blank lines and Markdown headings separate paragraphs; the headings themselves are not quoted. */
    private static List<String> paragraphs(String context) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : context.lines().map(String::strip).toList()) {
            if (line.isEmpty() || line.startsWith("#")) {
                flush(paragraphs, current);
            } else {
                current.append(current.isEmpty() ? "" : "\n").append(line);
            }
        }
        flush(paragraphs, current);
        return paragraphs;
    }

    private static void flush(List<String> paragraphs, StringBuilder current) {
        if (!current.isEmpty()) {
            paragraphs.add(current.toString());
            current.setLength(0);
        }
    }

    private static long overlap(String paragraph, List<String> questionWords) {
        return words(paragraph).stream().distinct().filter(questionWords::contains).count();
    }

    private static List<String> words(String text) {
        return List.of(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).stream()
                .filter(word -> word.length() >= 3).toList();
    }

    /** "make it 4" or "cancel it": the order is whichever the assistant last reported, like a model reading its memory. */
    private static Reply followUp(Matcher follow, List<Message> history, String question) {
        Optional<String> orderId = lastOrderId(history);
        if (orderId.isEmpty()) {
            return Reply.text(vietnamese(question) ? "Bạn muốn nói đơn nào?" : "Which order do you mean?");
        }
        return follow.group(1) != null
                ? Reply.tool("updateOrderQuantity",
                        "{\"orderId\":" + orderId.get() + ",\"newQuantity\":" + follow.group(1) + "}")
                : Reply.tool("cancelOrder", "{\"orderId\":" + orderId.get() + "}");
    }

    private static Optional<String> lastOrderId(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getMessageType() == MessageType.ASSISTANT) {
                Matcher id = ORDER_ID.matcher(String.valueOf(history.get(i).getText()));
                if (id.find()) {
                    return Optional.of(id.group(1));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> nameFrom(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getMessageType() == MessageType.USER) {
                Matcher name = NAME_GIVEN.matcher(RagPrompt.questionOf(String.valueOf(history.get(i).getText())));
                if (name.find()) {
                    return Optional.of(name.group(1));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean vietnamese(String question) {
        return NON_ASCII.matcher(question).find();
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
        if (data.has("sku")) {
            return data.path("name").asString() + " costs " + data.path("priceVnd").asLong() + " VND, "
                    + data.path("stock").asInt() + " in stock.";
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
