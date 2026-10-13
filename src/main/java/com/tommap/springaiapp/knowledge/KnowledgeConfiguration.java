package com.tommap.springaiapp.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KnowledgeProperties.class)
class KnowledgeConfiguration {

    static final String STORE = "knowledgeVectorStore";

    /**
     * Declared by hand so the table name is explicit (a second store will back the semantic cache); defining it
     * also makes the pgvector auto-configuration back off. Flyway owns the schema, so it is only validated here.
     */
    @Bean(STORE)
    VectorStore knowledgeVectorStore(JdbcTemplate jdbc, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbc, new TimingEmbeddingModel(embeddingModel))
                .vectorTableName("vector_store")
                .dimensions(1536)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(true)
                .build();
    }

    @Bean
    TokenTextSplitter tokenTextSplitter(KnowledgeProperties properties) {
        return TokenTextSplitter.builder().withChunkSize(properties.chunkTokens()).build();
    }
}
