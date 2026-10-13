package com.tommap.springaiapp.chat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Every paid model call, kept across demo resets because the money is really spent. */
@Service
public class UsageLedger {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final BudgetProperties budget;
    private final PricingProperties pricing;

    UsageLedger(JdbcTemplate jdbc, Clock clock, BudgetProperties budget, PricingProperties pricing) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.budget = budget;
        this.pricing = pricing;
    }

    /** Cost in USD of the given token counts at the active model's price. */
    BigDecimal cost(long tokensIn, long tokensOut) {
        return BigDecimal.valueOf(tokensIn).multiply(pricing.inputPerMtok())
                .add(BigDecimal.valueOf(tokensOut).multiply(pricing.outputPerMtok()))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    void record(long tokensIn, long tokensOut, BigDecimal costUsd) {
        jdbc.update("INSERT INTO usage_log (created_at, tokens_in, tokens_out, cost_usd) VALUES (?, ?, ?, ?)",
                Timestamp.from(clock.instant()), tokensIn, tokensOut, costUsd);
    }

    public BigDecimal totalUsd() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(cost_usd), 0) FROM usage_log", BigDecimal.class);
    }

    public BigDecimal limitUsd() {
        return budget.limitUsd();
    }

    public boolean exhausted() {
        return totalUsd().compareTo(budget.limitUsd()) >= 0;
    }
}
