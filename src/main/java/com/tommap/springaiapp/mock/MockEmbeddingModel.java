package com.tommap.springaiapp.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Deterministic bag-of-words embedding: texts that share words score higher, no network. */
public class MockEmbeddingModel implements EmbeddingModel {

    // Matches OpenAI text-embedding-3-small, so the mock fits the same vector columns.
    static final int DIMENSIONS = 1536;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embed(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        return normalize(vector);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private static float[] normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= (float) norm;
        }
        return vector;
    }
}
