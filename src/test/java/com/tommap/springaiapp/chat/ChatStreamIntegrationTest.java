package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChatStreamIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Test
    void streamEmitsTokensThenModelStepThenDone() throws Exception {
        String body = stream(login("an"), "xin chào");

        List<String> events = body.lines().filter(l -> l.startsWith("event:")).map(l -> l.substring(6).trim()).toList();
        assertThat(events).contains("token", "step", "done");
        assertThat(events.getLast()).isEqualTo("done");
        assertThat(events.indexOf("step")).isGreaterThan(events.lastIndexOf("token") - 1);

        String answer = dataOf(body, "token").stream().map(d -> JSON.readTree(d).get("text").asString())
                .reduce("", String::concat);
        assertThat(answer).contains("trợ lý Kefir");

        var modelStep = JSON.readTree(dataOf(body, "step").getFirst());
        assertThat(modelStep.get("kind").asString()).isEqualTo("model");
        assertThat(modelStep.get("detail").get("tokensIn").asInt()).isPositive();

        var done = JSON.readTree(dataOf(body, "done").getFirst());
        assertThat(done.get("tokensIn").asInt()).isPositive();
        assertThat(done.get("tokensOut").asInt()).isPositive();
        assertThat(done.get("cache").asString()).isEqualTo("MISS");
        assertThat(done.get("cost").asDouble()).isPositive();
    }

    @Test
    void streamRequiresAToken() throws Exception {
        mvc.perform(post("/api/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("hi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankOrOversizedMessageIsRejected() throws Exception {
        String token = login("an");
        for (String message : List.of("", "x".repeat(2001))) {
            mvc.perform(post("/api/chat/stream").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(requestJson(message)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void newConversationReturnsFreshId() throws Exception {
        String token = login("an");
        String first = mvc.perform(post("/api/chat/new").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/chat/new").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        assertThat(UUID.fromString(JSON.readTree(first).get("conversationId").asString())).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    private String stream(String token, String message) throws Exception {
        MvcResult started = mvc.perform(post("/api/chat/stream").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(message)))
                .andExpect(request().asyncStarted()).andReturn();
        // SSE is always UTF-8, but the content type carries no charset and MockMvc would decode as ISO-8859-1.
        return mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> dataOf(String body, String event) {
        List<String> lines = body.lines().toList();
        return java.util.stream.IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).equals("event:" + event))
                .mapToObj(i -> lines.get(i + 1).substring("data:".length()))
                .toList();
    }

    private static String requestJson(String message) {
        return "{\"conversationId\":\"" + UUID.randomUUID() + "\",\"message\":\"" + message + "\"}";
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(response).get("token").asString();
    }
}
