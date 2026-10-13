package com.tommap.springaiapp.chat;

import java.math.BigDecimal;
import java.time.Clock;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Every paid model call, kept across demo resets because the money is really spent. */
@Service
public class UsageLedger {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    UsageLedger(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    void record(long tokensIn, long tokensOut, BigDecimal costUsd) {
        jdbc.update("INSERT INTO usage_log (created_at, tokens_in, tokens_out, cost_usd) VALUES (?, ?, ?, ?)",
                Timestamp.from(clock.instant()), tokensIn, tokensOut, costUsd);
    }

    public BigDecimal totalUsd() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(cost_usd), 0) FROM usage_log", BigDecimal.class);
    }
}
