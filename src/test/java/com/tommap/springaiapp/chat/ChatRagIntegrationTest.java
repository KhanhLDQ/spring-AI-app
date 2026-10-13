package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class ChatRagIntegrationTest {

    private static final String SHELF_LIFE = """
            # Shelf life

            ## Hạn dùng / Shelf life
            Kefir Mật Ong để được dưới 3 ngày trong ngăn mát.
            Honey Kefir keeps under 3 days in the fridge.
            """;

    @Autowired
    MockMvc mvc;

    private ChatTestClient chat;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        chat = new ChatTestClient(mvc);
        admin = "Bearer " + chat.login("admin");
        mvc.perform(delete("/api/admin/knowledge").header("Authorization", admin)).andExpect(status().isNoContent());
        upload("shelf-life.md", SHELF_LIFE.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aKnowledgeQuestionIsAnsweredFromTheRetrievedChunk() throws Exception {
        var reply = chat.chat(chat.login("an"), "Kefir mật ong để được bao lâu?");

        JsonNode rag = ragStep(reply);
        assertThat(rag.get("title").asString()).isEqualTo("Retrieval: 1 chunk(s)");
        assertThat(rag.get("concept").asString()).isEqualTo("QuestionAnswerAdvisor");
        JsonNode chunk = rag.get("detail").get("chunks").get(0);
        assertThat(chunk.get("source").asString()).isEqualTo("shelf-life.md");
        assertThat(chunk.get("page").isNull()).isTrue();
        assertThat(chunk.get("score").asDouble()).isGreaterThanOrEqualTo(rag.get("detail").get("threshold").asDouble());
        assertThat(chunk.get("text").asString()).contains("Kefir Mật Ong để được dưới 3 ngày");
        assertThat(rag.get("detail").get("topK").asInt()).isEqualTo(3);

        assertThat(reply.answer()).contains("dưới 3 ngày");
        List<String> kinds = reply.data("step").stream().map(s -> s.get("kind").asString()).toList();
        assertThat(kinds).containsExactly("memory", "rag", "model");
    }

    @Test
    void whenNothingIsRelevantTheStepIsEmptyAndTheBotAdmitsItDoesNotKnow() throws Exception {
        var reply = chat.chat(chat.login("an"), "Shop giao ra Hà Nội không?");

        JsonNode rag = ragStep(reply);
        assertThat(rag.get("detail").get("chunks")).isEmpty();
        assertThat(rag.get("title").asString()).isEqualTo("Retrieval: no chunk above threshold");
        assertThat(reply.answer()).contains("không có thông tin");
    }

    @Test
    void uploadingTheOfferPdfTurnsAnIDontKnowIntoAnAnswerCitingPageTwo() throws Exception {
        String token = chat.login("an");
        String question = "Ưu đãi tháng 10 có combo nào không?";

        assertThat(chat.chat(token, question).answer()).contains("không có thông tin");

        upload("uu-dai-thang-10.pdf", Files.readAllBytes(Path.of("demo-assets/uu-dai-thang-10.pdf")));
        var reply = chat.chat(token, question);

        JsonNode chunks = ragStep(reply).get("detail").get("chunks");
        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.get("source").asString()).isEqualTo("uu-dai-thang-10.pdf");
            assertThat(chunk.get("page").asInt()).isEqualTo(2);
        });
        assertThat(reply.answer()).contains("Combo 6 chai Kefir Mật Ong");
    }

    @Test
    void anOrderStillReachesTheToolsEvenThoughRetrievalRuns() throws Exception {
        var reply = chat.chat(chat.login("an"), "order 1 honey kefir");

        assertThat(reply.toolSteps()).singleElement()
                .satisfies(step -> assertThat(step.get("detail").get("name").asString()).isEqualTo("createOrder"));
        assertThat(reply.data("step")).anySatisfy(step -> assertThat(step.get("kind").asString()).isEqualTo("rag"));
    }

    private static JsonNode ragStep(ChatTestClient.Reply reply) {
        return reply.data("step").stream().filter(s -> "rag".equals(s.get("kind").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("no rag step in " + reply.eventNames()));
    }

    private void upload(String filename, byte[] content) throws Exception {
        mvc.perform(multipart("/api/admin/knowledge")
                        .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                        .header("Authorization", admin))
                .andExpect(status().isOk());
    }
}
