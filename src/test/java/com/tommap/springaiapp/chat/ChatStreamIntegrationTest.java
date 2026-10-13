package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChatStreamIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    private ChatTestClient chat;

    @BeforeEach
    void setUp() {
        chat = new ChatTestClient(mvc);
    }

    @Test
    void streamEmitsTokensThenModelStepThenDone() throws Exception {
        ChatTestClient.Reply reply = chat.chat(chat.login("an"), "xin chào");

        List<String> events = reply.eventNames();
        assertThat(events).contains("token", "step", "done");
        assertThat(events.getLast()).isEqualTo("done");
        assertThat(events.indexOf("step")).isGreaterThan(events.lastIndexOf("token") - 1);
        assertThat(reply.answer()).contains("trợ lý Kefir");

        assertThat(reply.data("step").stream().map(step -> step.get("kind").asString()))
                .containsExactly("rag", "model");
        var modelStep = reply.data("step").getLast();
        assertThat(modelStep.get("detail").get("tokensIn").asInt()).isPositive();

        var done = reply.data("done").getFirst();
        assertThat(done.get("tokensIn").asInt()).isPositive();
        assertThat(done.get("tokensOut").asInt()).isPositive();
        assertThat(done.get("cache").asString()).isEqualTo("MISS");
        assertThat(done.get("cost").asDouble()).isPositive();
    }

    @Test
    void streamRequiresAToken() throws Exception {
        mvc.perform(post("/api/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .content(ChatTestClient.requestJson("hi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankOrOversizedMessageIsRejected() throws Exception {
        String token = chat.login("an");
        for (String message : List.of("", "x".repeat(2001))) {
            mvc.perform(post("/api/chat/stream").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(ChatTestClient.requestJson(message)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void newConversationReturnsFreshId() throws Exception {
        String token = chat.login("an");
        String first = mvc.perform(post("/api/chat/new").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/chat/new").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        assertThat(UUID.fromString(JSON.readTree(first).get("conversationId").asString())).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }
}
