package com.tommap.springaiapp.admin;

import com.tommap.springaiapp.cache.SemanticCache;
import com.tommap.springaiapp.chat.ChatHistoryService;
import com.tommap.springaiapp.knowledge.KnowledgeAdvisor;
import com.tommap.springaiapp.knowledge.KnowledgeService;
import com.tommap.springaiapp.knowledge.QueryRouter;
import com.tommap.springaiapp.order.OrderSeedService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Puts the demo back to a known state between runs. The model spend ledger is deliberately left alone. */
@Service
@Profile("dev | demo | mock")
class DemoService {

    private final OrderSeedService orders;
    private final KnowledgeService knowledge;
    private final ChatHistoryService history;
    private final SemanticCache cache;
    private final KnowledgeAdvisor rag;
    private final QueryRouter router;
    private final double defaultCacheThreshold;
    private final double defaultRagThreshold;
    private final double defaultRouteThreshold;

    DemoService(OrderSeedService orders, KnowledgeService knowledge, ChatHistoryService history, SemanticCache cache,
                KnowledgeAdvisor rag, QueryRouter router,
                @Value("${kefir.cache.similarity-threshold}") double defaultCacheThreshold,
                @Value("${kefir.knowledge.rag.similarity-threshold}") double defaultRagThreshold,
                @Value("${kefir.knowledge.router.similarity-threshold}") double defaultRouteThreshold) {
        this.orders = orders;
        this.knowledge = knowledge;
        this.history = history;
        this.cache = cache;
        this.rag = rag;
        this.router = router;
        this.defaultCacheThreshold = defaultCacheThreshold;
        this.defaultRagThreshold = defaultRagThreshold;
        this.defaultRouteThreshold = defaultRouteThreshold;
    }

    void reset() {
        orders.reseed();
        history.clearAll();
        knowledge.reseed();
        cache.clear();
        cache.threshold(defaultCacheThreshold);
        rag.threshold(defaultRagThreshold);
        router.threshold(defaultRouteThreshold);
    }

    void reseed() {
        orders.reseed();
    }

    void clearCache() {
        cache.clear();
    }

    void clearVectors() {
        knowledge.deleteAll();
    }

    void clearMemory() {
        history.clearAll();
    }

    Thresholds setThresholds(Double cacheValue, Double ragValue, Double routeValue) {
        if (cacheValue != null) {
            cache.threshold(cacheValue);
        }
        if (ragValue != null) {
            rag.threshold(ragValue);
        }
        if (routeValue != null) {
            router.threshold(routeValue);
        }
        return thresholds();
    }

    Thresholds thresholds() {
        return new Thresholds(cache.threshold(), rag.threshold(), router.threshold());
    }

    record Thresholds(double cache, double rag, double route) {
    }
}
