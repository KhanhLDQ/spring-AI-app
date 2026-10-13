package com.tommap.springaiapp.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.tommap.springaiapp.mock.MockEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

class TimingEmbeddingModelTest {

    private final TimingEmbeddingModel slow = new TimingEmbeddingModel(new MockEmbeddingModel() {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            sleep();
            return super.call(request);
        }

        private void sleep() {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    });

    @Test
    void countsOnlyTimeSpentEmbeddingOnTheCallingThread() {
        long embedMs = TimingEmbeddingModel.embedMillisDuring(() -> {
            slow.call(new EmbeddingRequest(java.util.List.of("a"), null));
            slow.call(new EmbeddingRequest(java.util.List.of("b"), null));
        });

        assertThat(embedMs).isBetween(55L, 500L);
    }

    @Test
    void eachMeasurementStartsFromZero() {
        TimingEmbeddingModel.embedMillisDuring(() -> slow.call(new EmbeddingRequest(java.util.List.of("a"), null)));

        assertThat(TimingEmbeddingModel.embedMillisDuring(() -> { })).isZero();
    }
}
