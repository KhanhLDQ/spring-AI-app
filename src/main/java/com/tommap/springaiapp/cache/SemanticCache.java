package com.tommap.springaiapp.cache;

import com.tommap.springaiapp.auth.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Answers remembered by the meaning of the question. An entry belongs to the role that asked (an administrator's
 * answer must not reach a customer) and expires after the configured time to live.
 */
@Service
public class SemanticCache {

    record Match(double score, String question, String answer, List<?> sources) {
    }

    private static final String ANSWER = "answer";
    private static final String SOURCES = "sources";
    private static final String ROLE = "role";
    private static final String CREATED_AT = "createdAt";

    private final VectorStore store;
    private final JdbcTemplate jdbc;
    private final CacheProperties properties;
    private final Clock clock;
    private volatile double threshold;

    SemanticCache(@Qualifier(CacheConfiguration.STORE) VectorStore store, JdbcTemplate jdbc,
                  CacheProperties properties, Clock clock) {
        this.store = store;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
        this.threshold = properties.similarityThreshold();
    }

    /** The closest live entry for this role, however low its score; the caller compares it with the threshold. */
    Optional<Match> nearest(String question, Role role) {
        FilterExpressionBuilder f = new FilterExpressionBuilder();
        long oldest = Instant.now(clock).minus(properties.ttl()).toEpochMilli();
        List<Document> found = store.similaritySearch(SearchRequest.builder()
                .query(question).topK(1).similarityThreshold(0.0)
                .filterExpression(f.and(f.eq(ROLE, role.name()), f.gte(CREATED_AT, oldest)).build())
                .build());
        return found.stream().findFirst().map(d ->
                new Match(d.getScore() == null ? 0 : d.getScore(), d.getText(), String.valueOf(d.getMetadata().get(ANSWER)),
                        d.getMetadata().get(SOURCES) instanceof List<?> sources ? sources : List.of()));
    }

    /** The sources travel with the answer, so a cached answer can still show which documents it came from. */
    void store(String question, String answer, Role role, List<Map<String, Object>> sources) {
        store.add(List.of(Document.builder().text(question)
                .metadata(Map.of(ANSWER, answer, ROLE, role.name(), CREATED_AT, clock.millis(), SOURCES, sources))
                .build()));
    }

    /** Called whenever the knowledge changes: every cached answer was built on the old documents. */
    public void clear() {
        jdbc.execute("TRUNCATE TABLE semantic_cache");
    }

    public long size() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM semantic_cache", Long.class);
    }

    public double threshold() {
        return threshold;
    }

    public void threshold(double value) {
        this.threshold = value;
    }
}
