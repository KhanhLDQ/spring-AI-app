package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import com.tommap.springaiapp.cache.SemanticCache;
import com.tommap.springaiapp.cache.SemanticCacheAdvisor;
import com.tommap.springaiapp.trace.TraceCollector;
import com.tommap.springaiapp.trace.TraceStep;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SemanticCacheIntegrationTest {

    private static final String QUESTION = "Kefir mật ong để được bao lâu?";
    private static final String PARAPHRASE = "Kefir mật ong để được bao lâu vậy?";
    private static final String SHELF_LIFE = """
            ## Hạn dùng / Shelf life
            Kefir Mật Ong để được dưới 3 ngày trong ngăn mát.
            Honey Kefir keeps under 3 days in the fridge.
            """;

    @Autowired
    MockMvc mvc;
    @Autowired
    SemanticCache cache;
    @Autowired
    ChatClient chatClient;
    @Autowired
    TraceCollector traces;

    private ChatTestClient chat;
    private String admin;
    private String user;

    @BeforeEach
    void setUp() throws Exception {
        chat = new ChatTestClient(mvc);
        admin = chat.login("admin");
        user = chat.login("an");
        mvc.perform(delete("/api/admin/knowledge").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        upload("shelf-life.md", SHELF_LIFE);
        assertThat(cache.size()).isZero();
    }

    @AfterEach
    void restoreThreshold() {
        cache.threshold(0.85);
    }

    @Test
    void aKnowledgeAnswerIsStoredAndTheSameQuestionIsThenServedFromTheCache() throws Exception {
        var first = chat.chat(user, QUESTION);

        assertThat(kinds(first)).containsExactly("cache", "memory", "rag", "model", "cachewrite");
        assertThat(step(first, "cache").get("detail").get("hit").asBoolean()).isFalse();
        assertThat(step(first, "cachewrite").get("detail").get("stored").asBoolean()).isTrue();
        assertThat(first.data("done").getFirst().get("cache").asString()).isEqualTo("MISS");

        var second = chat.chat(user, QUESTION);

        assertThat(kinds(second)).containsExactly("cache");
        JsonNode lookup = step(second, "cache").get("detail");
        assertThat(lookup.get("hit").asBoolean()).isTrue();
        assertThat(lookup.get("score").asDouble()).isGreaterThanOrEqualTo(lookup.get("threshold").asDouble());
        assertThat(lookup.get("matched").asString()).isEqualTo(QUESTION);
        assertThat(lookup.get("sources")).singleElement().satisfies(source -> {
            assertThat(source.get("source").asString()).isEqualTo("shelf-life.md");
            assertThat(source.get("text").asString()).contains("dưới 3 ngày");
        });
        assertThat(second.answer()).isEqualTo(first.answer());
        JsonNode done = second.data("done").getFirst();
        assertThat(done.get("cache").asString()).isEqualTo("HIT");
        assertThat(done.get("tokensIn").asInt()).isZero();
        assertThat(done.get("cost").asDouble()).isZero();
    }

    @Test
    void aCloseParaphraseHitsButAnUnrelatedQuestionDoesNot() throws Exception {
        chat.chat(user, QUESTION);

        assertThat(chat.chat(user, PARAPHRASE).data("done").getFirst().get("cache").asString()).isEqualTo("HIT");
        assertThat(chat.chat(user, "Shop giao ra Hà Nội không?").data("done").getFirst().get("cache").asString())
                .isEqualTo("MISS");
    }

    @Test
    void raisingTheThresholdTurnsTheSameParaphraseIntoAMiss() throws Exception {
        chat.chat(user, QUESTION);
        cache.threshold(0.99);

        var reply = chat.chat(user, PARAPHRASE);

        assertThat(step(reply, "cache").get("detail").get("threshold").asDouble()).isEqualTo(0.99);
        assertThat(reply.data("done").getFirst().get("cache").asString()).isEqualTo("MISS");
    }

    @Test
    void aHitIsWrittenIntoTheConversationSoLaterTurnsRemember() throws Exception {
        var first = chat.chat(user, QUESTION);
        UUID conversation = UUID.randomUUID();

        chat.chat(user, conversation, QUESTION);

        mvc.perform(get("/api/chat/history").param("conversationId", conversation.toString())
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].text").value(QUESTION))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].text").value(first.answer()));
    }

    @Test
    void answersAreKeptPerRoleSoACustomerNeverGetsAnAdministratorsAnswer() throws Exception {
        chat.chat(admin, QUESTION);

        assertThat(chat.chat(user, QUESTION).data("done").getFirst().get("cache").asString()).isEqualTo("MISS");
        assertThat(chat.chat(user, QUESTION).data("done").getFirst().get("cache").asString()).isEqualTo("HIT");
    }

    @Test
    void turnsThatCalledAToolAreNeverCached() throws Exception {
        for (int i = 0; i < 2; i++) {
            var reply = chat.chat(user, "order 1 honey kefir");

            assertThat(reply.toolSteps()).hasSize(1);
            assertThat(step(reply, "cachewrite").get("detail").get("reason").asString())
                    .isEqualTo("the turn called a tool");
        }
        assertThat(cache.size()).isZero();
    }

    @Test
    void answersWithoutKnowledgeBehindThemAreNotCached() throws Exception {
        var reply = chat.chat(user, "Shop giao ra Hà Nội không?");

        assertThat(step(reply, "cachewrite").get("detail").get("reason").asString()).isEqualTo("no knowledge was used");
        assertThat(cache.size()).isZero();
    }

    @Test
    void aQuestionAskedAfterEarlierMessagesIsNotCachedBecauseItMayDependOnThem() throws Exception {
        UUID conversation = UUID.randomUUID();
        chat.chat(user, conversation, "hello");

        var reply = chat.chat(user, conversation, QUESTION);

        assertThat(step(reply, "cachewrite").get("detail").get("reason").asString())
                .isEqualTo("depends on earlier messages");
        assertThat(cache.size()).isZero();
    }

    @Test
    void changingTheKnowledgeEmptiesTheCache() throws Exception {
        chat.chat(user, QUESTION);
        assertThat(cache.size()).isEqualTo(1);

        upload("promo.md", "## Promo\nBuy 3 get 1.");
        assertThat(cache.size()).isZero();

        chat.chat(user, QUESTION);
        assertThat(cache.size()).isEqualTo(1);
        mvc.perform(delete("/api/admin/knowledge").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(cache.size()).isZero();
    }

    @Test
    void theBlockingCallPathUsesTheCacheToo() {
        String first = ask("an:" + UUID.randomUUID(), "t-" + UUID.randomUUID());
        String secondTrace = "t-" + UUID.randomUUID();

        String second = ask("an:" + UUID.randomUUID(), secondTrace);

        assertThat(second).isEqualTo(first);
        assertThat(traces.finish(secondTrace)).extracting(TraceStep::kind).containsExactly("cache");
    }

    private String ask(String conversationKey, String traceId) {
        traces.begin(traceId);
        String answer = chatClient.prompt().user(QUESTION)
                .advisors(a -> a.param(TraceCollector.TRACE_ID_PARAM, traceId)
                        .param(ChatMemory.CONVERSATION_ID, conversationKey)
                        .param(SemanticCacheAdvisor.ROLE_PARAM, "USER"))
                .call().content();
        assertThat(answer).isNotBlank();
        return answer;
    }

    private void upload(String filename, String content) throws Exception {
        mvc.perform(multipart("/api/admin/knowledge")
                        .file(new MockMultipartFile("file", filename, "application/octet-stream",
                                content.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    private static List<String> kinds(ChatTestClient.Reply reply) {
        return reply.data("step").stream().map(s -> s.get("kind").asString()).toList();
    }

    private static JsonNode step(ChatTestClient.Reply reply, String kind) {
        return reply.data("step").stream().filter(s -> kind.equals(s.get("kind").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " step in " + kinds(reply)));
    }
}
