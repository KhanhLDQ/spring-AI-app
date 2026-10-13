package com.tommap.springaiapp.admin;

import com.tommap.springaiapp.cache.SemanticCache;
import com.tommap.springaiapp.chat.ChatHistoryService;
import com.tommap.springaiapp.knowledge.KnowledgeAdvisor;
import com.tommap.springaiapp.knowledge.KnowledgeService;
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
    private final double defaultCacheThreshold;
    private final double defaultRagThreshold;

    DemoService(OrderSeedService orders, KnowledgeService knowledge, ChatHistoryService history, SemanticCache cache,
                KnowledgeAdvisor rag, @Value("${kefir.cache.similarity-threshold}") double defaultCacheThreshold,
                @Value("${kefir.knowledge.rag.similarity-threshold}") double defaultRagThreshold) {
        this.orders = orders;
        this.knowledge = knowledge;
        this.history = history;
        this.cache = cache;
        this.rag = rag;
        this.defaultCacheThreshold = defaultCacheThreshold;
        this.defaultRagThreshold = defaultRagThreshold;
    }

    void reset() {
        orders.reseed();
        history.clearAll();
        knowledge.reseed();
        cache.clear();
        cache.threshold(defaultCacheThreshold);
        rag.threshold(defaultRagThreshold);
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

    Thresholds setThresholds(Double cacheValue, Double ragValue) {
        if (cacheValue != null) {
            cache.threshold(cacheValue);
        }
        if (ragValue != null) {
            rag.threshold(ragValue);
        }
        return thresholds();
    }

    Thresholds thresholds() {
        return new Thresholds(cache.threshold(), rag.threshold());
    }

    record Thresholds(double cache, double rag) {
    }
}
