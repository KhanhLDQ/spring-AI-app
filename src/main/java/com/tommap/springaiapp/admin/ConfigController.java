package com.tommap.springaiapp.admin;

import com.tommap.springaiapp.cache.SemanticCache;
import com.tommap.springaiapp.chat.UsageLedger;
import com.tommap.springaiapp.knowledge.KnowledgeAdvisor;
import com.tommap.springaiapp.knowledge.QueryRouter;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the demo is running on, for the Control Panel and the cost display. */
@RestController
@RequestMapping("/api/config")
class ConfigController {

    private final Environment environment;
    private final SemanticCache cache;
    private final KnowledgeAdvisor rag;
    private final QueryRouter router;
    private final UsageLedger usage;
    private final BigDecimal inputPerMtok;
    private final BigDecimal outputPerMtok;

    ConfigController(Environment environment, SemanticCache cache, KnowledgeAdvisor rag, QueryRouter router, UsageLedger usage,
                     @Value("${kefir.pricing.input-per-mtok}") BigDecimal inputPerMtok,
                     @Value("${kefir.pricing.output-per-mtok}") BigDecimal outputPerMtok) {
        this.environment = environment;
        this.cache = cache;
        this.rag = rag;
        this.router = router;
        this.usage = usage;
        this.inputPerMtok = inputPerMtok;
        this.outputPerMtok = outputPerMtok;
    }

    @GetMapping
    ConfigView config() {
        String[] profiles = environment.getActiveProfiles();
        return new ConfigView(
                environment.getProperty("spring.ai.anthropic.chat.options.model", "mock"),
                new Pricing(inputPerMtok, outputPerMtok),
                new Budget(usage.totalUsd(), usage.limitUsd()),
                new DemoService.Thresholds(cache.threshold(), rag.threshold(), router.threshold()),
                profiles.length == 0 ? "mock" : String.join(",", profiles));
    }

    record ConfigView(String model, Pricing pricing, Budget budget, DemoService.Thresholds thresholds,
                      String profile) {
    }

    record Pricing(BigDecimal inPerMTok, BigDecimal outPerMTok) {
    }

    record Budget(BigDecimal usedUsd, BigDecimal limitUsd) {
    }
}
