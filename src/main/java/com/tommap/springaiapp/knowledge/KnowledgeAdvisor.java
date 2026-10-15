package com.tommap.springaiapp.knowledge;

import com.tommap.springaiapp.chat.AdvisorSwitch;
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
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.scheduler.Scheduler;

/**
 * QuestionAnswerAdvisor behind a QueryRouter, plus Inspector steps for both. The vector store's own observation cannot be tied to a chat
 * turn (the search runs on a scheduler thread with no parent observation), so the step is recorded here, where
 * the turn's trace id is in the request context.
 */
public final class KnowledgeAdvisor implements BaseAdvisor {

    /**
     * Added to order requests, which skip retrieval. Without it the small model sometimes answers a follow-up like
     * "cancel it" with a made-up confirmation instead of calling the tool.
     */
    static final String TOOL_REMINDER = "\n\n(Reminder: use your tools for this request, and say it is done "
            + "only if a tool succeeded in this turn.)";

    private final VectorStore store;
    private final TraceCollector traces;
    private final QueryRouter router;
    private final int topK;
    private volatile double threshold;
    private volatile QuestionAnswerAdvisor retrieval;

    KnowledgeAdvisor(VectorStore store, TraceCollector traces, QueryRouter router, int topK, double threshold) {
        this.store = store;
        this.traces = traces;
        this.router = router;
        this.topK = topK;
        threshold(threshold);
    }

    public double threshold() {
        return threshold;
    }

    /** QuestionAnswerAdvisor is immutable, so a new threshold means a new instance; turns in flight keep the old one. */
    public void threshold(double value) {
        this.retrieval = QuestionAnswerAdvisor.builder(store)
                .searchRequest(SearchRequest.builder().topK(topK).similarityThreshold(value).build())
                .promptTemplate(new PromptTemplate(RagPrompt.TEMPLATE))
                .order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 50)
                .build();
        this.threshold = value;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String traceId = request.context().get(TraceCollector.TRACE_ID_PARAM) instanceof String id ? id : null;
        if (AdvisorSwitch.isOff(request.context(), AdvisorSwitch.RAG)) {
            if (traceId != null) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("off", true);
                detail.put("chunks", List.of());
                traces.record(traceId, new TraceStep("rag", "Retrieval: off", "QuestionAnswerAdvisor",
                        (System.nanoTime() - traces.startNanos(traceId)) / 1_000_000, 0, detail));
            }
            return request;
        }
        long routeStart = System.nanoTime();
        QueryRouter.Decision decision = router.route(request.prompt().getUserMessage().getText());
        long routeEnd = System.nanoTime();
        if (traceId != null) {
            long startMs = (routeStart - traces.startNanos(traceId)) / 1_000_000;
            traces.record(traceId, routeStep(decision, startMs, (routeEnd - routeStart) / 1_000_000));
        }
        if (decision.skipRetrieval()) {
            // The reminder only makes sense when the model has tools to call.
            if (AdvisorSwitch.isOff(request.context(), AdvisorSwitch.TOOLS)) {
                return request;
            }
            return request.mutate().prompt(request.prompt().augmentUserMessage(message ->
                    message.mutate().text(message.getText() + TOOL_REMINDER).build())).build();
        }
        long start = System.nanoTime();
        ChatClientRequest augmented = retrieval.before(request, chain);
        long end = System.nanoTime();
        if (traceId != null) {
            long startMs = (start - traces.startNanos(traceId)) / 1_000_000;
            traces.record(traceId, step(retrieved(augmented), startMs, (end - start) / 1_000_000));
        }
        return augmented;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        if (AdvisorSwitch.isOff(response.context(), AdvisorSwitch.RAG)) {
            return response;
        }
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

    private TraceStep routeStep(QueryRouter.Decision decision, long startMs, long durMs) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("skip", decision.skipRetrieval());
        detail.put("transactional", decision.transactional());
        detail.put("knowledge", decision.knowledge());
        detail.put("threshold", router.threshold());
        detail.put("margin", router.margin());
        String title = decision.skipRetrieval() ? "Route: order request, retrieval skipped" : "Route: retrieve knowledge";
        return new TraceStep("route", title, "QueryRouter", startMs, durMs, detail);
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
