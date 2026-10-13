package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import com.tommap.springaiapp.order.OrderSeedService;
import com.tommap.springaiapp.order.RevenueService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RevenueBriefIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RevenueService revenue;
    @Autowired
    UsageLedger ledger;
    @Autowired
    JsonMapper json;
    @Autowired
    OrderSeedService seed;

    @BeforeEach
    void resetShop() {
        seed.reseed();
    }

    @AfterEach
    void removeTheSpend() {
        jdbc.update("DELETE FROM usage_log WHERE tokens_in = -1");
    }

    @Test
    void theModelFillsInTheTypedBriefAndTheCallIsBilled() throws Exception {
        ChatTestClient chat = new ChatTestClient(mvc);
        String admin = "Bearer " + chat.login("admin");
        long callsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM usage_log", Long.class);

        mvc.perform(post("/api/admin/revenue/brief").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brief.headline", not(emptyString())))
                .andExpect(jsonPath("$.brief.highlights", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.brief.suggestion", not(emptyString())))
                .andExpect(jsonPath("$.summary.orders", is(3)))
                .andExpect(jsonPath("$.schema", containsString("headline")))
                .andExpect(jsonPath("$.raw", containsString("\"suggestion\"")))
                .andExpect(jsonPath("$.tokensIn", greaterThan(0)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usage_log", Long.class)).isEqualTo(callsBefore + 1);
    }

    @Test
    void onlyAdministratorsAndOnlyWithinTheBudget() throws Exception {
        ChatTestClient chat = new ChatTestClient(mvc);
        mvc.perform(post("/api/admin/revenue/brief").header("Authorization", "Bearer " + chat.login("an")))
                .andExpect(status().isForbidden());

        jdbc.update("INSERT INTO usage_log (created_at, tokens_in, tokens_out, cost_usd) VALUES (now(), -1, 0, ?)",
                ledger.limitUsd());
        mvc.perform(post("/api/admin/revenue/brief").header("Authorization", "Bearer " + chat.login("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aModelAnswerThatIsNotTheRequestedJsonIsReportedNotThrownRaw() {
        ChatModel rambling = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("Sure! Here is a summary: sales went down."))));
            }
        };
        RevenueBriefService service = new RevenueBriefService(rambling, revenue, ledger, json);

        assertThatThrownBy(() -> service.brief(null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("did not return a usable brief");
    }
}
