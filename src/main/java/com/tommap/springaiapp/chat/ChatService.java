package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.auth.AuthenticatedUser;
import com.tommap.springaiapp.auth.ToolCaller;
import com.tommap.springaiapp.cache.SemanticCacheAdvisor;
import com.tommap.springaiapp.catalog.CatalogTools;
import com.tommap.springaiapp.order.OrderTools;
import com.tommap.springaiapp.order.ShopTime;
import com.tommap.springaiapp.trace.TraceCollector;
import com.tommap.springaiapp.trace.TraceStep;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(60);

    private final ChatClient chatClient;
    private final TraceCollector traces;
    private final CatalogTools catalogTools;
    private final OrderTools orderTools;
    private final UsageLedger usage;
    private final Clock clock;

    ChatService(ChatClient chatClient, TraceCollector traces, CatalogTools catalogTools,
                OrderTools orderTools, UsageLedger usage, Clock clock) {
        this.chatClient = chatClient;
        this.traces = traces;
        this.catalogTools = catalogTools;
        this.orderTools = orderTools;
        this.usage = usage;
        this.clock = clock;
    }

    Flux<ChatEvent> stream(AuthenticatedUser user, UUID conversationId, String message) {
        if (usage.exhausted()) {
            log.warn("Chat turn refused: budget of {} USD is spent", usage.limitUsd());
            return Flux.just(new ChatEvent.Error("The demo budget ($%s) is spent, so the model is paused. Raise kefir.budget.limit-usd to continue."
                    .formatted(usage.limitUsd().stripTrailingZeros().toPlainString())));
        }
        String traceId = UUID.randomUUID().toString();
        traces.begin(traceId);
        long startNanos = System.nanoTime();

        // Built here on the request thread: SecurityContextHolder is empty inside reactor threads and tools.
        String memoryKey = memoryKey(user, conversationId);
        Map<String, Object> toolContext = ToolCaller.contextFor(user, memoryKey);

        ChatClient.ChatClientRequestSpec prompt = chatClient.prompt()
                .system(SystemPrompts.forRole(user.role(), ShopTime.today(clock)))
                .user(message)
                .advisors(a -> a.param(TraceCollector.TRACE_ID_PARAM, traceId).param(ChatMemory.CONVERSATION_ID, memoryKey)
                        .param(SemanticCacheAdvisor.ROLE_PARAM, user.role().name()))
                .tools(catalogTools, orderTools)
                .toolContext(toolContext);
        Flux<ChatEvent> tokens = Flux.defer(() -> {
            RoundBreaks breaks = new RoundBreaks(() -> toolStepCount(traceId));
            return prompt.stream().chatResponse().mapNotNull(breaks::next).map(ChatEvent.Token::new);
        });

        return tokens
                .timeout(TURN_TIMEOUT)
                .concatWith(Flux.defer(() -> finish(traceId, startNanos)))
                .onErrorResume(error -> {
                    traces.finish(traceId);
                    log.warn("Chat turn failed: {}", error.getClass().getSimpleName());
                    return Flux.just(new ChatEvent.Error(errorMessage(error)));
                });
    }

    /** What the user is told; the cause stays in the log, since provider errors can carry request details. */
    static String errorMessage(Throwable error) {
        if (error instanceof TimeoutException) {
            return "The model did not answer within " + TURN_TIMEOUT.toSeconds() + " seconds. Please try again.";
        }
        return "The model is unavailable right now. Please try again shortly.";
    }

    private int toolStepCount(String traceId) {
        return (int) traces.snapshot(traceId).stream().filter(step -> "tool".equals(step.kind())).count();
    }

    /** The caller's name is part of the key, so no one can read or continue another user's conversation. */
    static String memoryKey(AuthenticatedUser user, UUID conversationId) {
        return user.username() + ":" + conversationId;
    }

    /**
     * The tool loop streams every model round into one text flow, so what the model says before a tool call and
     * what it says after run together. The tool-call chunk itself never reaches this stream, but the tool's trace
     * step does, so a blank line is inserted when a tool has run since the last text.
     */
    static final class RoundBreaks {

        private final IntSupplier toolsRun;
        private int toolsSeen;
        private boolean sawText;

        RoundBreaks(IntSupplier toolsRun) {
            this.toolsRun = toolsRun;
        }

        String next(ChatResponse response) {
            Generation generation = response.getResult();
            String text = generation == null ? null : generation.getOutput().getText();
            if (text == null || text.isEmpty()) {
                return null;
            }
            int run = toolsRun.getAsInt();
            boolean afterTool = run > toolsSeen;
            toolsSeen = run;
            boolean separate = afterTool && sawText;
            sawText = true;
            return separate ? "\n\n" + text : text;
        }
    }

    private Flux<ChatEvent> finish(String traceId, long startNanos) {
        List<TraceStep> steps = traces.finish(traceId);
        long tokensIn = sum(steps, "tokensIn");
        long tokensOut = sum(steps, "tokensOut");
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        BigDecimal cost = usage.cost(tokensIn, tokensOut);
        if (tokensIn + tokensOut > 0) {
            usage.record(tokensIn, tokensOut, cost);
        }
        return Flux.concat(
                Flux.fromIterable(steps).map(ChatEvent.Step::new),
                Flux.just(new ChatEvent.Done(ms, tokensIn, tokensOut, cost, cacheOutcome(steps))));
    }

    private static String cacheOutcome(List<TraceStep> steps) {
        boolean hit = steps.stream().anyMatch(s -> "cache".equals(s.kind()) && Boolean.TRUE.equals(s.detail().get("hit")));
        return hit ? "HIT" : "MISS";
    }

    private static long sum(List<TraceStep> steps, String key) {
        return steps.stream()
                .filter(s -> "model".equals(s.kind()))
                .map(s -> s.detail().get(key))
                .filter(Number.class::isInstance)
                .mapToLong(v -> ((Number) v).longValue())
                .sum();
    }
}
