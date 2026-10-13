package com.tommap.springaiapp.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import com.tommap.springaiapp.chat.ChatTestClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoControlIntegrationTest {

    private static final String FAQ = "Kefir mật ong để được bao lâu?";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private ChatTestClient chats;
    private String admin;
    private String user;

    @BeforeEach
    void setUp() throws Exception {
        chats = new ChatTestClient(mvc);
        admin = "Bearer " + login("admin");
        user = "Bearer " + login("an");
        post("/api/admin/demo/reset", admin);
    }

    @Test
    void onlyAdministratorsCanUseTheControls() throws Exception {
        post("/api/admin/demo/reset", user).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/demo/threshold").header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cache\":0.9}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetBringsEveryPieceOfDemoStateBack() throws Exception {
        mvc.perform(put("/api/admin/demo/threshold").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cache\":0.99,\"rag\":0.9}"));
        jdbc.update("DELETE FROM orders");
        chat(user, FAQ);
        post("/api/admin/demo/clear-vectors", admin);

        post("/api/admin/demo/reset", admin).andExpect(status().isNoContent());

        assertThat(count("orders")).isEqualTo(6);
        assertThat(count("spring_ai_chat_memory")).isZero();
        assertThat(count("semantic_cache")).isZero();
        assertThat(count("vector_store")).isEqualTo(48);
        mvc.perform(get("/api/config").header("Authorization", admin))
                .andExpect(jsonPath("$.thresholds.cache", is(0.85)))
                .andExpect(jsonPath("$.thresholds.rag", is(0.25)));
    }

    @Test
    void eachClearOnlyTouchesItsOwnState() throws Exception {
        chat(user, FAQ);
        assertThat(count("semantic_cache")).isPositive();
        assertThat(count("spring_ai_chat_memory")).isPositive();

        post("/api/admin/demo/clear-cache", admin).andExpect(status().isNoContent());
        assertThat(count("semantic_cache")).isZero();
        assertThat(count("spring_ai_chat_memory")).isPositive();

        post("/api/admin/demo/clear-memory", admin).andExpect(status().isNoContent());
        assertThat(count("spring_ai_chat_memory")).isZero();
        assertThat(count("vector_store")).isEqualTo(48);

        post("/api/admin/demo/clear-vectors", admin).andExpect(status().isNoContent());
        assertThat(count("vector_store")).isZero();
        assertThat(count("knowledge_docs")).isZero();
    }

    @Test
    void reseedRestoresSampleOrdersAndStock() throws Exception {
        jdbc.update("DELETE FROM orders");

        post("/api/admin/demo/reseed", admin).andExpect(status().isNoContent());

        assertThat(count("orders")).isEqualTo(6);
    }

    @Test
    void ragThresholdChangesWhatRetrievalReturns() throws Exception {
        assertThat(chat(user, FAQ)).contains("\"kind\":\"rag\"").doesNotContain("\"chunks\":[]");

        mvc.perform(put("/api/admin/demo/threshold").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rag\":0.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rag", is(0.99)))
                .andExpect(jsonPath("$.cache", is(0.85)));

        post("/api/admin/demo/clear-cache", admin);
        assertThat(chat(user, FAQ)).contains("\"chunks\":[]");
    }

    @Test
    void thresholdMustBeAScore() throws Exception {
        mvc.perform(put("/api/admin/demo/threshold").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cache\":1.5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void configShowsModelPricingAndSpend() throws Exception {
        BigDecimal before = jdbc.queryForObject("SELECT COALESCE(SUM(cost_usd), 0) FROM usage_log", BigDecimal.class);
        chat(user, "xin chào");

        mvc.perform(get("/api/config").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model", is("mock")))
                .andExpect(jsonPath("$.profile", is("mock")))
                .andExpect(jsonPath("$.pricing.inPerMTok", is(2)))
                .andExpect(jsonPath("$.budget.limitUsd", is(5)));
        BigDecimal after = jdbc.queryForObject("SELECT COALESCE(SUM(cost_usd), 0) FROM usage_log", BigDecimal.class);
        assertThat(after).isGreaterThan(before);
    }

    private String chat(String bearer, String message) throws Exception {
        return chats.chat(bearer.substring("Bearer ".length()), message).body();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private ResultActions post(String url, String bearer) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url)
                .header("Authorization", bearer));
    }

    private String login(String username) throws Exception {
        return chats.login(username);
    }
}
