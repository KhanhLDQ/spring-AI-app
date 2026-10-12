package com.tommap.springaiapp.chat;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** USD per million tokens for the active chat model; keeps Inspector cost in line with the invoice. */
@ConfigurationProperties("kefir.pricing")
record PricingProperties(BigDecimal inputPerMtok, BigDecimal outputPerMtok) {
}
