package com.tommap.springaiapp.knowledge;

import com.tommap.springaiapp.trace.TraceCollector;
import com.tommap.springaiapp.trace.TraceStep;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import reactor.core.scheduler.Scheduler;

/**
 * QuestionAnswerAdvisor plus an Inspector step. The vector store's own observation cannot be tied to a chat
 * turn (the search runs on a scheduler thread with no parent observation), so the step is recorded here, where
 * the turn's trace id is in the request context.
 */
public final class KnowledgeAdvisor implements BaseAdvisor {

    private final QuestionAnswerAdvisor retrieval;
    private final TraceCollector traces;
    private final int topK;
    private final double threshold;

    KnowledgeAdvisor(QuestionAnswerAdvisor retrieval, TraceCollector traces, int topK, double threshold) {
        this.retrieval = retrieval;
        this.traces = traces;
        this.topK = topK;
        this.threshold = threshold;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        long start = System.nanoTime();
        ChatClientRequest augmented = retrieval.before(request, chain);
        long end = System.nanoTime();
        if (request.context().get(TraceCollector.TRACE_ID_PARAM) instanceof String traceId) {
            long startMs = (start - traces.startNanos(traceId)) / 1_000_000;
            traces.record(traceId, step(retrieved(augmented), startMs, (end - start) / 1_000_000));
        }
        return augmented;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return retrieval.after(response, chain);
    }

    @Override
    public int getOrder() {
        return retrieval.getOrder();
    }

    @Override
    public Scheduler getScheduler() {
        return retrieval.getScheduler();
    }

    @SuppressWarnings("unchecked")
    private static List<Document> retrieved(ChatClientRequest request) {
        Object documents = request.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        return documents == null ? List.of() : (List<Document>) documents;
    }

    private TraceStep step(List<Document> documents, long startMs, long durMs) {
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (Document document : documents) {
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("source", document.getMetadata().get(KnowledgeReader.SOURCE));
            chunk.put("page", document.getMetadata().get(KnowledgeReader.PAGE));
            chunk.put("score", document.getScore());
            chunk.put("text", document.getText());
            chunks.add(chunk);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("chunks", chunks);
        detail.put("threshold", threshold);
        detail.put("topK", topK);
        String title = chunks.isEmpty() ? "Retrieval: no chunk above threshold"
                : "Retrieval: " + chunks.size() + " chunk(s)";
        return new TraceStep("rag", title, "QuestionAnswerAdvisor", startMs, durMs, detail);
    }
}
