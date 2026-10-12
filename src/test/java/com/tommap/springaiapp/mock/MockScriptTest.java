package com.tommap.springaiapp.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

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
    void smallTalkNeedsNoTool() {
        assertThat(reply("hello there").hasToolCalls()).isFalse();
        assertThat(reply("xin chào").text()).startsWith("Xin chào");
        assertThat(reply("this is something else").text()).startsWith("This is a simulated answer");
    }
}
