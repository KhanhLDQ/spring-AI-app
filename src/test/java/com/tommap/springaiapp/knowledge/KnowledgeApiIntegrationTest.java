package com.tommap.springaiapp.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KnowledgeApiIntegrationTest {

    private static final String SMALL_DOC = "# Title\n\n## Alpha\nfirst section\n\n## Beta\nsecond section\n";

    @Autowired
    MockMvc mvc;
    @Autowired
    KnowledgeService knowledge;
    @Autowired
    KnowledgeSeedRunner seedRunner;
    @Autowired
    JdbcTemplate jdbc;

    private String admin;
    private String user;

    @BeforeEach
    void setUp() throws Exception {
        knowledge.deleteAll();
        seedRunner.run(null);
        admin = "Bearer " + login("admin");
        user = "Bearer " + login("an");
    }

    @Test
    void bundledShopDocumentsAreLoadedOnFirstStart() throws Exception {
        mvc.perform(get("/api/admin/knowledge").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[?(@.filename=='faq-cua-hang.md')].chunkCount").value(12));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vector_store", Long.class)).isEqualTo(48);
    }

    @Test
    void seedingAgainKeepsExistingDocumentsUntouched() throws Exception {
        seedRunner.run(null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_docs", Long.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vector_store", Long.class)).isEqualTo(48);
    }

    @Test
    void markdownIsSplitIntoOneChunkPerSectionAndReportsEtlTimings() throws Exception {
        upload("notes.md", SMALL_DOC.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename", is("notes.md")))
                .andExpect(jsonPath("$.chunkCount", is(2)))
                .andExpect(jsonPath("$.etl.chunks", is(2)))
                .andExpect(jsonPath("$.etl.readMs", greaterThan(-1)))
                .andExpect(jsonPath("$.etl.splitMs", greaterThan(-1)))
                .andExpect(jsonPath("$.etl.embedMs", greaterThan(-1)))
                .andExpect(jsonPath("$.etl.storeMs", greaterThan(-1)));

        mvc.perform(get("/api/admin/knowledge").header("Authorization", admin))
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].filename", is("notes.md")));
    }

    @Test
    void windowsLineEndingsDoNotLeakIntoChunks() throws Exception {
        upload("crlf.md", SMALL_DOC.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount", is(2)));

        mvc.perform(post("/api/admin/knowledge/search").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"first section\"}"))
                .andExpect(jsonPath("$[?(@.source=='crlf.md')].text", everyItem(not(containsString("\r")))));
    }

    @Test
    void pdfKeepsPageNumbersSoAnswersCanCiteThem() throws Exception {
        byte[] pdf = Files.readAllBytes(Path.of("demo-assets/uu-dai-thang-10.pdf"));

        upload("uu-dai-thang-10.pdf", pdf)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount", greaterThan(0)));

        mvc.perform(post("/api/admin/knowledge/search").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"ưu đãi tháng 10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source=='uu-dai-thang-10.pdf')].page").isNotEmpty())
                .andExpect(jsonPath("$[?(@.source=='uu-dai-thang-10.pdf')].text", everyItem(not(containsString("  ")))));
    }

    @Test
    void sameContentOrSameNameIsRejectedAsDuplicate() throws Exception {
        upload("notes.md", SMALL_DOC.getBytes(StandardCharsets.UTF_8)).andExpect(status().isOk());

        upload("copy.md", SMALL_DOC.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already in the knowledge base")));
        upload("notes.md", "## Other\ndifferent text".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_docs", Long.class)).isEqualTo(8);
    }

    @Test
    void clientPathsInTheFilenameAreStripped() throws Exception {
        upload("../../etc/passwd.md", SMALL_DOC.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename", is("passwd.md")));
        upload("C:\\temp\\evil<b>\"x\".md", "## Heading\nbody".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename", is("evil_b__x_.md")));
    }

    @Test
    void unsupportedTypesAreRefusedWhateverTheExtensionClaims() throws Exception {
        upload("notes.txt", SMALL_DOC.getBytes(StandardCharsets.UTF_8)).andExpect(status().isUnsupportedMediaType());
        upload("fake.pdf", SMALL_DOC.getBytes(StandardCharsets.UTF_8)).andExpect(status().isUnsupportedMediaType());
        upload("binary.md", new byte[] {'#', ' ', 0, 1, 2, (byte) 0xff}).andExpect(status().isUnsupportedMediaType());
        upload("broken.pdf", "%PDF-1.7 this is not a real pdf".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isUnprocessableContent());
        upload("empty.md", new byte[0]).andExpect(status().isUnprocessableContent());
    }

    @Test
    void oversizedFilesAreRefused() throws Exception {
        byte[] big = new byte[11 * 1024 * 1024];
        Arrays.fill(big, (byte) 'a');

        upload("big.md", big).andExpect(status().isPayloadTooLarge());
    }

    @Test
    void pdfsWithTooManyPagesOrNoTextLayerAreRefused() throws Exception {
        upload("long.pdf", blankPdf(51)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("50 pages")));
        upload("scan.pdf", blankPdf(2)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("OCR")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_docs", Long.class)).isEqualTo(7);
    }

    @Test
    void deletingClearsDocumentsVectorsAndTheSemanticCache() throws Exception {
        jdbc.update("""
                INSERT INTO semantic_cache (content, metadata, embedding)
                VALUES ('q', '{}', (SELECT ARRAY(SELECT 0.1 FROM generate_series(1, 1536)))::vector)""");

        mvc.perform(delete("/api/admin/knowledge").header("Authorization", admin))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/knowledge").header("Authorization", admin))
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vector_store", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM semantic_cache", Long.class)).isZero();
    }

    @Test
    void searchReturnsScoredChunksWithoutCallingTheModel() throws Exception {
        String body = mvc.perform(post("/api/admin/knowledge/search").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"Kefir mật ong để được bao lâu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].source", containsString(".md")))
                .andExpect(jsonPath("$[0].page", nullValue()))
                .andExpect(jsonPath("$[0].text", not("")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<Double> scores = JsonMapper.builder().build().readTree(body).valueStream()
                .map(hit -> hit.get("score").asDouble()).toList();
        assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder()).allMatch(score -> score > 0 && score <= 1);
    }

    @Test
    void searchRejectsABlankQuery() throws Exception {
        mvc.perform(post("/api/admin/knowledge/search").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsMayUseTheKnowledgeEndpoints() throws Exception {
        mvc.perform(get("/api/admin/knowledge").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/knowledge")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/knowledge").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/knowledge")
                        .file(new MockMultipartFile("file", "a.md", "text/markdown", SMALL_DOC.getBytes()))
                        .header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    private ResultActions upload(String filename, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/admin/knowledge")
                .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                .header("Authorization", admin));
    }

    /** Pages without any text, which is what a scanned document looks like to a text extractor. */
    private static byte[] blankPdf(int pages) throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                pdf.addPage(new PDPage());
            }
            pdf.save(out);
            return out.toByteArray();
        }
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(body).get("token").asString();
    }
}
