package com.tommap.springaiapp.knowledge;

import com.tommap.springaiapp.trace.TraceCollector;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
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
                                      TraceCollector traces) {
        KnowledgeProperties.Rag rag = properties.rag();
        QuestionAnswerAdvisor retrieval = QuestionAnswerAdvisor.builder(store)
                .searchRequest(SearchRequest.builder()
                        .topK(rag.topK()).similarityThreshold(rag.similarityThreshold()).build())
                .promptTemplate(new PromptTemplate(RagPrompt.TEMPLATE))
                .order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 50)
                .build();
        return new KnowledgeAdvisor(retrieval, traces, rag.topK(), rag.similarityThreshold());
    }

    @Bean
    TokenTextSplitter tokenTextSplitter(KnowledgeProperties properties) {
        return TokenTextSplitter.builder().withChunkSize(properties.chunkTokens()).build();
    }
}
