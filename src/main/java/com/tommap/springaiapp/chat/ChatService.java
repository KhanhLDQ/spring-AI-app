package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.auth.AuthenticatedUser;
import com.tommap.springaiapp.auth.ToolCaller;
import com.tommap.springaiapp.catalog.CatalogTools;
import com.tommap.springaiapp.order.OrderTools;
import com.tommap.springaiapp.trace.TraceCollector;
import com.tommap.springaiapp.trace.TraceStep;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(60);

    private final ChatClient chatClient;
    private final TraceCollector traces;
    private final PricingProperties pricing;
    private final CatalogTools catalogTools;
    private final OrderTools orderTools;

    ChatService(ChatClient chatClient, TraceCollector traces, PricingProperties pricing, CatalogTools catalogTools,
                OrderTools orderTools) {
        this.chatClient = chatClient;
        this.traces = traces;
        this.pricing = pricing;
        this.catalogTools = catalogTools;
        this.orderTools = orderTools;
    }

    Flux<ChatEvent> stream(AuthenticatedUser user, UUID conversationId, String message) {
        String traceId = UUID.randomUUID().toString();
        traces.begin(traceId);
        long startNanos = System.nanoTime();

        // Built here on the request thread: SecurityContextHolder is empty inside reactor threads and tools.
        String memoryKey = memoryKey(user, conversationId);
        Map<String, Object> toolContext = ToolCaller.contextFor(user, memoryKey);

        Flux<ChatEvent> tokens = chatClient.prompt()
                .system(SystemPrompts.forRole(user.role()))
                .user(message)
                .advisors(a -> a.param(TraceCollector.TRACE_ID_PARAM, traceId).param(ChatMemory.CONVERSATION_ID, memoryKey))
                .tools(catalogTools, orderTools)
                .toolContext(toolContext)
                .stream()
                .content()
                .map(ChatEvent.Token::new);

        return tokens
                .timeout(TURN_TIMEOUT)
                .concatWith(Flux.defer(() -> finish(traceId, startNanos)))
                .onErrorResume(error -> {
                    traces.finish(traceId);
                    log.warn("Chat turn failed: {}", error.getClass().getSimpleName());
                    return Flux.just(new ChatEvent.Error("The model is unavailable right now. Please try again shortly."));
                });
    }

    /** The caller's name is part of the key, so no one can read or continue another user's conversation. */
    static String memoryKey(AuthenticatedUser user, UUID conversationId) {
        return user.username() + ":" + conversationId;
    }

    private Flux<ChatEvent> finish(String traceId, long startNanos) {
        List<TraceStep> steps = traces.finish(traceId);
        long tokensIn = sum(steps, "tokensIn");
        long tokensOut = sum(steps, "tokensOut");
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        return Flux.concat(
                Flux.fromIterable(steps).map(ChatEvent.Step::new),
                Flux.just(new ChatEvent.Done(ms, tokensIn, tokensOut, cost(tokensIn, tokensOut), "MISS")));
    }

    private static long sum(List<TraceStep> steps, String key) {
        return steps.stream()
                .filter(s -> "model".equals(s.kind()))
                .map(s -> s.detail().get(key))
                .filter(Number.class::isInstance)
                .mapToLong(v -> ((Number) v).longValue())
                .sum();
    }

    private BigDecimal cost(long tokensIn, long tokensOut) {
        return BigDecimal.valueOf(tokensIn).multiply(pricing.inputPerMtok())
                .add(BigDecimal.valueOf(tokensOut).multiply(pricing.outputPerMtok()))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
