package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.tommap.springaiapp.TestcontainersConfiguration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BudgetLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsageLedger ledger;

    @AfterEach
    void removeTheSpend() {
        jdbc.update("DELETE FROM usage_log WHERE tokens_in = -1");
    }

    @Test
    void aSpentBudgetRefusesTheTurnBeforeAnyModelWork() throws Exception {
        ChatTestClient chat = new ChatTestClient(mvc);
        String token = chat.login("an");
        jdbc.update("INSERT INTO usage_log (created_at, tokens_in, tokens_out, cost_usd) VALUES (now(), -1, 0, ?)",
                ledger.limitUsd());
        assertThat(ledger.exhausted()).isTrue();

        ChatTestClient.Reply reply = chat.chat(token, "xin chào");

        assertThat(reply.eventNames()).containsExactly("error");
        assertThat(reply.data("error").getFirst().get("message").asString()).contains("budget").contains("paused");
    }

    @Test
    void underTheLimitTurnsRunNormally() throws Exception {
        ChatTestClient chat = new ChatTestClient(mvc);

        ChatTestClient.Reply reply = chat.chat(chat.login("an"), "xin chào");

        assertThat(reply.eventNames()).contains("done").doesNotContain("error");
    }

    @Test
    void aTimeoutIsExplainedAndOtherFailuresStayGeneric() {
        assertThat(ChatService.errorMessage(new TimeoutException())).contains("did not answer within 60 seconds");
        assertThat(ChatService.errorMessage(new IllegalStateException("401 key sk-secret")))
                .isEqualTo("The model is unavailable right now. Please try again shortly.")
                .doesNotContain("sk-secret");
    }
}
