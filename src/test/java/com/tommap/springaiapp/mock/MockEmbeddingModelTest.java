package com.tommap.springaiapp.mock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockEmbeddingModelTest {

    private final MockEmbeddingModel model = new MockEmbeddingModel();

    @Test
    void sameTextGivesSameUnitVector() {
        float[] a = model.embed("kefir mật ong để được bao lâu");
        float[] b = model.embed("kefir mật ong để được bao lâu");

        assertThat(a).hasSize(model.dimensions()).isEqualTo(b);
        assertThat(cosine(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void sharedWordsScoreHigherThanUnrelatedText() {
        float[] question = model.embed("kefir mật ong để được bao lâu");
        float[] related = model.embed("kefir mật ong bảo quản được mấy ngày");
        float[] unrelated = model.embed("shop giao hàng ra Hà Nội không");

        assertThat(cosine(question, related)).isGreaterThan(cosine(question, unrelated));
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
