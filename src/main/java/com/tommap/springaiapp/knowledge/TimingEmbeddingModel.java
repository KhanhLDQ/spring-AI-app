package com.tommap.springaiapp.knowledge;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * VectorStore.add embeds and inserts in one call; this wrapper times the embedding part so the ETL can report
 * embed and store separately. PgVectorStore embeds on the calling thread, so a ThreadLocal is enough.
 */
class TimingEmbeddingModel implements EmbeddingModel {

    private static final ThreadLocal<long[]> NANOS = ThreadLocal.withInitial(() -> new long[1]);

    private final EmbeddingModel delegate;

    TimingEmbeddingModel(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    /** Runs the work and returns how many milliseconds of it were spent embedding on this thread. */
    static long embedMillisDuring(Runnable work) {
        long[] counter = NANOS.get();
        counter[0] = 0;
        work.run();
        return counter[0] / 1_000_000;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return timed(() -> delegate.call(request));
    }

    @Override
    public float[] embed(Document document) {
        return timed(() -> delegate.embed(document));
    }

    @Override
    public float[] embed(String text) {
        return timed(() -> delegate.embed(text));
    }

    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy strategy) {
        return timed(() -> delegate.embed(documents, options, strategy));
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    private static <T> T timed(Supplier<T> call) {
        long start = System.nanoTime();
        try {
            return call.get();
        } finally {
            NANOS.get()[0] += System.nanoTime() - start;
        }
    }
}
