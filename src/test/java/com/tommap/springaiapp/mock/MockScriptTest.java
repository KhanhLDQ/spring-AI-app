package com.tommap.springaiapp.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.tommap.springaiapp.knowledge.RagPrompt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;

class MockScriptTest {

    private static MockScript.Reply reply(String question) {
        return MockScript.replyTo(List.<Message>of(new UserMessage(question)));
    }

    @Test
    void orderRequestBecomesACreateOrderToolCall() {
        var call = reply("order 5 honey kefir 500ml").toolCalls().getFirst();

        assertThat(call.name()).isEqualTo("createOrder");
        assertThat(call.arguments()).isEqualTo("{\"product\":\"MAT-ONG-500\",\"quantity\":5}");
    }

    @Test
    void vietnameseRequestsAreRecognisedToo() {
        assertThat(reply("đặt 3 chai kefir mè đen").toolCalls().getFirst().arguments())
                .isEqualTo("{\"product\":\"ME-DEN-330\",\"quantity\":3}");
        assertThat(reply("huỷ đơn 1042").toolCalls().getFirst().name()).isEqualTo("cancelOrder");
    }

    @Test
    void priceQuestionBecomesAGetProductToolCall() {
        var call = reply("how much is honey kefir 500ml").toolCalls().getFirst();

        assertThat(call.name()).isEqualTo("getProduct");
        assertThat(call.arguments()).isEqualTo("{\"product\":\"MAT-ONG-500\"}");
    }

    @Test
    void smallTalkNeedsNoTool() {
        assertThat(reply("hello there").hasToolCalls()).isFalse();
        assertThat(reply("xin chào").text()).startsWith("Xin chào");
        assertThat(reply("this is something else").text()).startsWith("This is a simulated answer");
    }

    private static MockScript.Reply withContext(String question, String context) {
        return reply(new PromptTemplate(RagPrompt.TEMPLATE)
                .render(Map.of("query", question, "question_answer_context", context)));
    }

    @Test
    void knowledgeQuestionQuotesTheBestMatchingParagraphs() {
        String context = """
                ## Menu overview
                Mayiu Kefir sells four lines of kefir.

                ## Shelf life
                Honey kefir keeps under 3 days in the fridge.
                """;

        var reply = withContext("How long does honey kefir keep?", context);

        assertThat(reply.hasToolCalls()).isFalse();
        assertThat(reply.text()).startsWith("Honey kefir keeps under 3 days");
    }

    @Test
    void emptyRetrievalMeansIDontKnowInTheLanguageOfTheQuestion() {
        assertThat(withContext("Shop giao ra Hà Nội không?", "").text()).startsWith("Xin lỗi, mình không có thông tin");
        assertThat(withContext("Do you deliver to Hanoi?", "").text()).startsWith("Sorry, I don't have that information");
    }

    @Test
    void wordsInTheRetrievedContextNeverTriggerTools() {
        var reply = withContext("How long does honey kefir keep?", "## Menu overview\nOrder online, see all products.");

        assertThat(reply.hasToolCalls()).isFalse();
    }

    @Test
    void followUpEditsTheOrderTheAssistantLastReported() {
        var conversation = List.<Message>of(
                new UserMessage("order 2 honey kefir"),
                new AssistantMessage("Done. Order #1048 is PENDING, total 76000 VND."),
                new UserMessage("make it 4"));

        var call = MockScript.replyTo(conversation).toolCalls().getFirst();

        assertThat(call.name()).isEqualTo("updateOrderQuantity");
        assertThat(call.arguments()).isEqualTo("{\"orderId\":1048,\"newQuantity\":4}");
    }

    @Test
    void followUpWithNothingToRefIsAQuestion() {
        assertThat(reply("cancel it").text()).isEqualTo("Which order do you mean?");
        assertThat(reply("huỷ nó").text()).isEqualTo("Bạn muốn nói đơn nào?");
    }

    @Test
    void nameIsRememberedFromEarlierUserMessages() {
        var conversation = List.<Message>of(
                new UserMessage("Tên mình là Lan"),
                new AssistantMessage("Rất vui được gặp bạn, Lan!"),
                new UserMessage("mình tên gì?"));

        assertThat(MockScript.replyTo(conversation).text()).isEqualTo("Tên bạn là Lan.");
        assertThat(reply("what is my name?").text()).isEqualTo("I don't know your name yet.");
    }
}
