package com.tommap.springaiapp.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.tommap.springaiapp.mock.MockEmbeddingModel;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Labelled messages against the bundled examples, using the word-overlap mock embedding (no network). */
class QueryRouterTest {

    private static final double THRESHOLD = 0.30;
    private static final double MARGIN = 0.12;

    private final QueryRouter router = QueryRouter.withBundledExamples(new MockEmbeddingModel(), THRESHOLD, MARGIN);

    /** true = retrieval must be skipped, false = retrieval must run. Mixed messages must always retrieve. */
    private static final Map<String, Boolean> LABELLED = new LinkedHashMap<>();

    static {
        LABELLED.put("Cho mình đặt 3 chai kefir mật ong", true);
        LABELLED.put("Đặt giúp mình 4 chai kefir vị cà phê", true);
        LABELLED.put("Huỷ đơn #1042 giúp mình", true);
        LABELLED.put("Order 2 honey kefir", true);
        LABELLED.put("Cancel my last order", true);
        LABELLED.put("Show my orders", true);
        LABELLED.put("Duyệt đơn #1031", true);
        LABELLED.put("Kefir mật ong để được bao lâu?", false);
        LABELLED.put("Kefir mật ong bảo quản được bao lâu vậy?", false);
        LABELLED.put("Shop giao ra Hà Nội không?", false);
        LABELLED.put("What promotions are on this month?", false);
        LABELLED.put("Trời nóng kefir sủi bọt, có hỏng không?", false);
        LABELLED.put("Xin chào", false);
        LABELLED.put("Kefir mật ong để được bao lâu, và đặt giúp mình 2 chai", false);
        LABELLED.put("Shop có giao hàng ra Hà Nội không, nếu có thì cho mình đặt 3 chai kefir", false);
    }

    @Test
    void routesLabelledMessagesCorrectly() {
        LABELLED.forEach((message, expectedSkip) -> {
            QueryRouter.Decision decision = router.route(message);
            assertThat(decision.skipRetrieval()).as("%s (order %.2f, knowledge %.2f)", message,
                    decision.transactional(), decision.knowledge()).isEqualTo(expectedSkip);
        });
    }

    @Test
    void raisingTheThresholdMakesTheRouterRetrieveForEverything() {
        router.threshold(1.01);

        assertThat(router.route("Order 2 honey kefir").skipRetrieval()).isFalse();
    }

    @Test
    void aFailingEmbeddingFallsBackToRetrieval() {
        QueryRouter broken = QueryRouter.withBundledExamples(new MockEmbeddingModel() {
            @Override
            public float[] embed(String text) {
                throw new IllegalStateException("embedding down");
            }
        }, THRESHOLD, MARGIN);

        assertThat(broken.route("Order 2 honey kefir").skipRetrieval()).isFalse();
    }
}
