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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/** Each pipeline part can be switched off per message; the Inspector still shows it, marked off. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdvisorSwitchIntegrationTest {

    private static final String KNOWLEDGE_QUESTION = "Kefir mật ong để được bao lâu?";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private ChatTestClient chat;
    private String user;

    @BeforeEach
    void setUp() throws Exception {
        chat = new ChatTestClient(mvc);
        user = chat.login("an");
    }

    @Test
    void withMemoryOffTheBotForgetsAndTheTurnIsNotSaved() throws Exception {
        UUID conversation = UUID.randomUUID();
        chat.chat(user, conversation, "my name is Lan");
        int saved = memoryRows();

        ChatTestClient.Reply off = chat.chat(user, conversation, "what is my name?", List.of("memory"));

        assertThat(off.answer()).isEqualTo("I don't know your name yet.");
        assertThat(step(off, "memory").get("detail").get("off").asBoolean()).isTrue();
        assertThat(memoryRows()).isEqualTo(saved);
        assertThat(chat.chat(user, conversation, "what is my name?").answer()).isEqualTo("Your name is Lan.");
    }

    @Test
    void withRagOffNothingIsRetrievedAndTheRouterDoesNotRun() throws Exception {
        ChatTestClient.Reply reply = chat.chat(user, UUID.randomUUID(), KNOWLEDGE_QUESTION, List.of("rag"));

        JsonNode rag = step(reply, "rag");
        assertThat(rag.get("detail").get("off").asBoolean()).isTrue();
        assertThat(rag.get("detail").get("chunks")).isEmpty();
        assertThat(kinds(reply)).doesNotContain("route");
    }

    @Test
    void withCacheOffThereIsNoLookupAndNoWrite() throws Exception {
        ChatTestClient.Reply reply = chat.chat(user, UUID.randomUUID(), KNOWLEDGE_QUESTION, List.of("cache"));

        assertThat(step(reply, "cache").get("detail").get("off").asBoolean()).isTrue();
        assertThat(kinds(reply)).doesNotContain("cachewrite");
        assertThat(reply.data("done").getFirst().get("cache").asString()).isEqualTo("OFF");
    }

    @Test
    void withToolsOffTheModelCannotPlaceAnOrder() throws Exception {
        int ordersBefore = orderRows();

        ChatTestClient.Reply reply = chat.chat(user, UUID.randomUUID(), "order 2 honey kefir", List.of("tools"));

        assertThat(reply.toolSteps()).isEmpty();
        assertThat(reply.answer()).contains("no tools are available");
        assertThat(orderRows()).isEqualTo(ordersBefore);
    }

    @Test
    void everythingStaysOnWhenNothingIsSwitchedOff() throws Exception {
        ChatTestClient.Reply reply = chat.chat(user, UUID.randomUUID(), "order 2 honey kefir");

        assertThat(reply.toolSteps()).hasSize(1);
        assertThat(kinds(reply)).contains("cache", "memory", "route");
        assertThat(reply.data("step")).noneMatch(s -> s.get("detail").has("off"));
    }

    @Test
    void anUnknownSwitchIsRejected() throws Exception {
        mvc.perform(post("/api/chat/stream").header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + UUID.randomUUID() + "\",\"message\":\"hi\",\"off\":[\"security\"]}"))
                .andExpect(status().isBadRequest());
    }

    private static JsonNode step(ChatTestClient.Reply reply, String kind) {
        return reply.data("step").stream().filter(s -> kind.equals(s.get("kind").asString())).findFirst().orElseThrow();
    }

    private static List<String> kinds(ChatTestClient.Reply reply) {
        return reply.data("step").stream().map(s -> s.get("kind").asString()).toList();
    }

    private int memoryRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM spring_ai_chat_memory", Integer.class);
    }

    private int orderRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
    }
}
