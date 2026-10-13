package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import com.tommap.springaiapp.order.OrderService;
import com.tommap.springaiapp.order.OrderStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChatMemoryIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    OrderService orders;
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
    void theBotRemembersEarlierTurnsOfTheSameConversation() throws Exception {
        UUID conversation = UUID.randomUUID();

        assertThat(chat.chat(user, conversation, "my name is Lan").answer()).contains("Nice to meet you, Lan");

        assertThat(chat.chat(user, conversation, "what is my name?").answer()).isEqualTo("Your name is Lan.");
    }

    @Test
    void aNewConversationStartsWithNoMemory() throws Exception {
        chat.chat(user, UUID.randomUUID(), "my name is Lan");

        assertThat(chat.chat(user, UUID.randomUUID(), "what is my name?").answer())
                .isEqualTo("I don't know your name yet.");
    }

    @Test
    void anotherUserCannotContinueTheConversationEvenWithItsId() throws Exception {
        UUID conversation = UUID.randomUUID();
        chat.chat(user, conversation, "my name is Lan");

        assertThat(chat.chat(chat.login("admin"), conversation, "what is my name?").answer())
                .isEqualTo("I don't know your name yet.");
    }

    @Test
    void theMemoryStepCountsTheMessagesItLoaded() throws Exception {
        UUID conversation = UUID.randomUUID();

        var first = chat.chat(user, conversation, "my name is Lan");
        var second = chat.chat(user, conversation, "what is my name?");

        assertThat(memoryStep(first).get("detail").get("messagesLoaded").asInt()).isZero();
        assertThat(memoryStep(second).get("detail").get("messagesLoaded").asInt()).isEqualTo(2);
        assertThat(memoryStep(second).get("detail").get("windowSize").asInt()).isEqualTo(20);
        assertThat(second.data("step").stream().map(s -> s.get("kind").asString()))
                .containsExactly("memory", "rag", "model");
    }

    @Test
    void aFollowUpLikeMakeItFourEditsTheOrderJustPlaced() throws Exception {
        UUID conversation = UUID.randomUUID();
        chat.chat(user, conversation, "order 2 honey kefir");
        long placed = orders.listMine("an").stream().mapToLong(o -> o.id()).max().orElseThrow();

        var reply = chat.chat(user, conversation, "make it 4");

        assertThat(reply.toolSteps()).singleElement()
                .satisfies(step -> assertThat(step.get("detail").get("name").asString()).isEqualTo("updateOrderQuantity"));
        assertThat(orders.listMine("an")).filteredOn(o -> o.id() == placed).singleElement()
                .satisfies(o -> {
                    assertThat(o.status()).isEqualTo(OrderStatus.PENDING);
                    assertThat(o.items().getFirst().quantity()).isEqualTo(4);
                });
    }

    @Test
    void historyReturnsOnlyTheCallersMessagesInOrder() throws Exception {
        UUID conversation = UUID.randomUUID();
        chat.chat(user, conversation, "my name is Lan");
        chat.chat(user, conversation, "what is my name?");

        mvc.perform(get("/api/chat/history").param("conversationId", conversation.toString())
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].role", contains("user", "assistant", "user", "assistant")))
                .andExpect(jsonPath("$[0].text").value("my name is Lan"))
                .andExpect(jsonPath("$[3].text").value("Your name is Lan."));

        mvc.perform(get("/api/chat/history").param("conversationId", conversation.toString())
                        .header("Authorization", "Bearer " + chat.login("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void historyNeedsAValidConversationIdAndALogin() throws Exception {
        mvc.perform(get("/api/chat/history").param("conversationId", "not-a-uuid")
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/chat/history").param("conversationId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void messagesArePersistedUnderTheUsersOwnKey() throws Exception {
        UUID conversation = UUID.randomUUID();
        chat.chat(user, conversation, "my name is Lan");

        List<String> types = jdbc.queryForList(
                "SELECT type FROM spring_ai_chat_memory WHERE conversation_id = ? ORDER BY sequence_id",
                String.class, "an:" + conversation);
        assertThat(types).containsExactly("USER", "ASSISTANT");
    }

    private static JsonNode memoryStep(ChatTestClient.Reply reply) {
        return reply.data("step").stream().filter(s -> "memory".equals(s.get("kind").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("no memory step in " + reply.eventNames()));
    }
}
