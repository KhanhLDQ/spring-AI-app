package com.tommap.springaiapp.cache;

import com.tommap.springaiapp.auth.Role;
import com.tommap.springaiapp.trace.TraceCollector;
import com.tommap.springaiapp.trace.TraceStep;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Outermost advisor: a question close enough in meaning to an answered one is answered from the cache without
 * reaching memory, retrieval or the model. A miss runs the chain, and the answer is remembered only when it can be
 * reused by someone else: it came from the knowledge base, needed no tool, and did not depend on earlier messages.
 */
public final class SemanticCacheAdvisor implements CallAdvisor, StreamAdvisor {

    /** Advisor-context parameter carrying the caller's role (a {@link Role} name). */
    public static final String ROLE_PARAM = "kefir.role";

    private static final int WORDS_PER_CHUNK = 4;

    private final SemanticCache cache;
    private final ChatMemory chatMemory;
    private final TraceCollector traces;

    SemanticCacheAdvisor(SemanticCache cache, ChatMemory chatMemory, TraceCollector traces) {
        this.cache = cache;
        this.chatMemory = chatMemory;
        this.traces = traces;
    }

    /** What the lookup decided (hit carries the cached answer) and how much conversation came before the question. */
    private record Lookup(boolean hit, String answer, int earlierMessages) {
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Turn turn = Turn.of(request).orElse(null);
        if (turn == null) {
            return chain.nextCall(request);
        }
        Lookup lookup = lookup(turn);
        if (lookup.hit()) {
            return responseOf(request, lookup.answer());
        }
        ChatClientResponse response = chain.nextCall(request);
        afterMiss(turn, lookup.earlierMessages(), text(response));
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Turn turn = Turn.of(request).orElse(null);
        if (turn == null) {
            return chain.nextStream(request);
        }
        // Embedding the question is a blocking call, so it runs off the reactive threads.
        return Mono.fromCallable(() -> lookup(turn)).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(lookup -> lookup.hit()
                        ? Flux.fromIterable(chunks(lookup.answer())).map(text -> responseOf(request, text))
                        : new ChatClientMessageAggregator().aggregateChatClientResponse(
                                chain.nextStream(request), response -> afterMiss(turn, lookup.earlierMessages(), text(response))));
    }

    @Override
    public String getName() {
        return "SemanticCacheAdvisor";
    }

    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 100;
    }

    /** A hit also writes the exchange into the conversation, because the memory advisor never sees this turn. */
    private Lookup lookup(Turn turn) {
        int earlier = chatMemory.get(turn.conversationId()).size();
        long start = System.nanoTime();
        Optional<SemanticCache.Match> nearest = cache.nearest(turn.question(), turn.role());
        long end = System.nanoTime();
        double threshold = cache.threshold();
        double score = nearest.map(SemanticCache.Match::score).orElse(0.0);
        boolean hit = nearest.isPresent() && score >= threshold;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("score", score);
        detail.put("threshold", threshold);
        detail.put("hit", hit);
        detail.put("matched", nearest.map(SemanticCache.Match::question).orElse(null));
        if (hit) {
            detail.put("sources", nearest.get().sources());
        }
        record(turn, "cache", hit ? "Semantic cache: HIT" : "Semantic cache: miss", start, end, detail);
        if (!hit) {
            return new Lookup(false, null, earlier);
        }
        String answer = nearest.get().answer();
        chatMemory.add(turn.conversationId(), List.of(new UserMessage(turn.question()), new AssistantMessage(answer)));
        return new Lookup(true, answer, earlier);
    }

    private void afterMiss(Turn turn, int earlierMessages, String answer) {
        long start = System.nanoTime();
        String refusal = whyNotReusable(turn, earlierMessages, answer);
        if (refusal == null) {
            cache.store(turn.question(), answer, turn.role(), sourcesOf(traces.snapshot(turn.traceId())));
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stored", refusal == null);
        detail.put("reason", refusal == null ? "answer can be reused" : refusal);
        record(turn, "cachewrite", refusal == null ? "Cache write: stored" : "Cache write: skipped",
                start, System.nanoTime(), detail);
    }

    private String whyNotReusable(Turn turn, int earlierMessages, String answer) {
        List<TraceStep> steps = traces.snapshot(turn.traceId());
        if (answer == null || answer.isBlank()) {
            return "empty answer";
        }
        if (steps.stream().anyMatch(s -> "tool".equals(s.kind()))) {
            return "the turn called a tool";
        }
        if (steps.stream().noneMatch(s -> "rag".equals(s.kind()) && hasChunks(s))) {
            return "no knowledge was used";
        }
        if (earlierMessages > 0) {
            return "depends on earlier messages";
        }
        return null;
    }

    /** The chunks the answer was built from, as the Inspector reports them, minus the scores. */
    private static List<Map<String, Object>> sourcesOf(List<TraceStep> steps) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (TraceStep step : steps) {
            if ("rag".equals(step.kind()) && step.detail().get("chunks") instanceof List<?> chunks) {
                for (Object chunk : chunks) {
                    if (chunk instanceof Map<?, ?> c) {
                        Map<String, Object> source = new LinkedHashMap<>();
                        source.put("source", c.get("source"));
                        source.put("page", c.get("page"));
                        source.put("text", c.get("text"));
                        sources.add(source);
                    }
                }
            }
        }
        return sources;
    }

    private static boolean hasChunks(TraceStep step) {
        return step.detail().get("chunks") instanceof List<?> chunks && !chunks.isEmpty();
    }

    private void record(Turn turn, String kind, String title, long startNanos, long endNanos, Map<String, Object> detail) {
        long startMs = (startNanos - traces.startNanos(turn.traceId())) / 1_000_000;
        traces.record(turn.traceId(), new TraceStep(kind, title, "SemanticCacheAdvisor", startMs,
                (endNanos - startNanos) / 1_000_000, detail));
    }

    private static ChatClientResponse responseOf(ChatClientRequest request, String text) {
        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))))
                .context(request.context())
                .build();
    }

    /** A stored answer is replayed in a few pieces so the page streams it like any other answer. */
    private static List<String> chunks(String answer) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int words = 0;
        for (String piece : answer.split("(?<=\\s)")) {
            current.append(piece);
            if (++words % WORDS_PER_CHUNK == 0) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static String text(ChatClientResponse response) {
        return response.chatResponse() == null || response.chatResponse().getResult() == null
                ? null : response.chatResponse().getResult().getOutput().getText();
    }

    /** Everything the advisor needs from the request; absent when the call did not come from the chat endpoint. */
    private record Turn(String question, Role role, String traceId, String conversationId) {

        static Optional<Turn> of(ChatClientRequest request) {
            Map<String, Object> context = request.context();
            String question = request.prompt().getUserMessage().getText();
            if (!(context.get(TraceCollector.TRACE_ID_PARAM) instanceof String traceId)
                    || !(context.get(ChatMemory.CONVERSATION_ID) instanceof String conversationId)
                    || !(context.get(ROLE_PARAM) instanceof String role)
                    || question == null || question.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Turn(question, Role.valueOf(role), traceId, conversationId));
        }
    }
}
