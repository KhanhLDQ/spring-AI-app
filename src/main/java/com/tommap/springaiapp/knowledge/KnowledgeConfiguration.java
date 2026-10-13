package com.tommap.springaiapp.knowledge;

import com.tommap.springaiapp.trace.TraceCollector;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
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

    /**
     * Sits inside memory and outside the tool loop: it must run once per turn, and a second pass over the
     * already augmented message (as happens to advisors inside the tool loop) would append the context twice.
     */
    @Bean
    KnowledgeAdvisor knowledgeAdvisor(@Qualifier(STORE) VectorStore store, KnowledgeProperties properties,
                                      TraceCollector traces, QueryRouter router) {
        KnowledgeProperties.Rag rag = properties.rag();
        return new KnowledgeAdvisor(store, traces, router, rag.topK(), rag.similarityThreshold());
    }

    @Bean
    QueryRouter queryRouter(EmbeddingModel embeddingModel, KnowledgeProperties properties) {
        KnowledgeProperties.Router router = properties.router();
        return QueryRouter.withBundledExamples(embeddingModel, router.similarityThreshold(), router.margin());
    }

    /** Runs in the background after start-up: the app is usable at once and the first message is not slowed. */
    @Bean
    ApplicationRunner routerWarmUp(QueryRouter router) {
        return args -> CompletableFuture.runAsync(router::warmUp);
    }
}
