package com.tommap.springaiapp.cache;

import com.tommap.springaiapp.trace.TraceCollector;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CacheProperties.class)
class CacheConfiguration {

    static final String STORE = "semanticCacheVectorStore";

    /** The second pgvector store: same embedding model as the knowledge base, its own table. */
    @Bean(STORE)
    VectorStore semanticCacheVectorStore(JdbcTemplate jdbc, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbc, embeddingModel)
                .vectorTableName("semantic_cache")
                .dimensions(1536)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(true)
                .build();
    }

    @Bean
    SemanticCacheAdvisor semanticCacheAdvisor(SemanticCache cache, ChatMemory chatMemory, TraceCollector traces) {
        return new SemanticCacheAdvisor(cache, chatMemory, traces);
    }
}
