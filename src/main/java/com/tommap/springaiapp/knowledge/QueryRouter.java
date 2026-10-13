package com.tommap.springaiapp.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.ClassPathResource;

/**
 * Decides whether a message needs the knowledge base. The message is embedded and compared with two small
 * groups of example messages: ones that act on orders and ones the knowledge base answers. Retrieval is skipped
 * only when the message is clearly closer to the order examples; anything unclear, including a message that
 * asks a question and places an order at once, still retrieves, because a missing chunk costs more than an
 * unneeded one.
 */
public final class QueryRouter {

    private static final Logger log = LoggerFactory.getLogger(QueryRouter.class);

    /** Best cosine score against each group; skipRetrieval is true only for a clear order request. */
    public record Decision(boolean skipRetrieval, double transactional, double knowledge) {
    }

    private final EmbeddingModel embeddings;
    private final Supplier<List<String>> transactionalExamples;
    private final Supplier<List<String>> knowledgeExamples;
    private final double margin;
    private volatile double threshold;
    private volatile List<float[]> transactionalVectors;
    private volatile List<float[]> knowledgeVectors;

    QueryRouter(EmbeddingModel embeddings, Supplier<List<String>> transactionalExamples,
                Supplier<List<String>> knowledgeExamples, double threshold, double margin) {
        this.embeddings = embeddings;
        this.transactionalExamples = transactionalExamples;
        this.knowledgeExamples = knowledgeExamples;
        this.threshold = threshold;
        this.margin = margin;
    }

    /** The examples shipped in src/main/resources/router, one message per line. */
    static QueryRouter withBundledExamples(EmbeddingModel embeddings, double threshold, double margin) {
        return new QueryRouter(embeddings, () -> examples("router/transactional.txt"),
                () -> examples("router/knowledge.txt"), threshold, margin);
    }

    private static List<String> examples(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read router examples " + path, e);
        }
    }

    public double threshold() {
        return threshold;
    }

    public void threshold(double value) {
        this.threshold = value;
    }

    public double margin() {
        return margin;
    }

    public Decision route(String message) {
        try {
            ensureExamples();
            float[] query = embeddings.embed(message);
            double transactional = best(query, transactionalVectors);
            double knowledge = best(query, knowledgeVectors);
            boolean skip = transactional >= threshold && transactional - knowledge >= margin;
            return new Decision(skip, transactional, knowledge);
        } catch (RuntimeException e) {
            log.warn("Query routing failed, retrieving anyway: {}", e.getMessage());
            return new Decision(false, 0, 0);
        }
    }

    /** Embedded on first use, so starting the app never calls the embedding API. */
    private void ensureExamples() {
        if (knowledgeVectors != null) {
            return;
        }
        synchronized (this) {
            if (knowledgeVectors == null) {
                transactionalVectors = embeddings.embed(transactionalExamples.get());
                knowledgeVectors = embeddings.embed(knowledgeExamples.get());
            }
        }
    }

    private static double best(float[] query, List<float[]> examples) {
        double best = -1;
        for (float[] example : examples) {
            best = Math.max(best, cosine(query, example));
        }
        return best;
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / Math.sqrt(normA * normB);
    }
}
