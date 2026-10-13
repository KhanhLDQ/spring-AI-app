package com.tommap.springaiapp.chat;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spending cap in USD across every paid model call; once the ledger reaches it, chat turns are refused. */
@ConfigurationProperties("kefir.budget")
record BudgetProperties(BigDecimal limitUsd) {
}
