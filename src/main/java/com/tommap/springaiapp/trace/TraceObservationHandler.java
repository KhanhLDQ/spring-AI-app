package com.tommap.springaiapp.trace;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.stereotype.Component;

/**
 * Turns the observations Spring AI already emits (model, tool) into Inspector steps. The owning chat
 * turn is found by walking up to the chat client observation, which carries the trace id.
 */
@Component
class TraceObservationHandler implements ObservationHandler<Observation.Context> {

    private static final String START_KEY = "kefir.startNanos";
    private static final String OPEN_KEY = "kefir.openTrace";
    private static final int MAX_TEXT = 300;

    private final TraceCollector collector;

    TraceObservationHandler(TraceCollector collector) {
        this.collector = collector;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext || context instanceof ToolCallingObservationContext;
    }

    @Override
    public void onStart(Observation.Context context) {
        context.put(START_KEY, System.nanoTime());
        String traceId = traceId(context);
        if (traceId != null) {
            context.put(OPEN_KEY, traceId);
            collector.opened(traceId);
        }
    }

    @Override
    public void onStop(Observation.Context context) {
        String traceId = traceId(context);
        Long start = context.get(START_KEY);
        try {
            if (traceId != null && start != null) {
                recordStep(context, traceId, start);
            }
        } finally {
            if (context.get(OPEN_KEY) instanceof String opened) {
                collector.closed(opened);
            }
        }
    }

    private void recordStep(Observation.Context context, String traceId, long start) {
        long traceStart = collector.startNanos(traceId);
        long startMs = (start - traceStart) / 1_000_000;
        long durMs = (System.nanoTime() - start) / 1_000_000;
        TraceStep step = switch (context) {
            case ChatModelObservationContext model -> modelStep(model, startMs, durMs);
            case ToolCallingObservationContext tool -> toolStep(tool, startMs, durMs);
            default -> null;
        };
        if (step != null) {
            collector.record(traceId, step);
        }
    }

    private static TraceStep modelStep(ChatModelObservationContext context, long startMs, long durMs) {
        ChatResponse response = context.getResponse();
        Map<String, Object> detail = new LinkedHashMap<>();
        String modelName = "model";
        if (response != null && response.getMetadata() != null) {
            modelName = response.getMetadata().getModel();
            Usage usage = response.getMetadata().getUsage();
            detail.put("model", modelName);
            detail.put("tokensIn", usage.getPromptTokens());
            detail.put("tokensOut", usage.getCompletionTokens());
            if (response.getResult() != null) {
                detail.put("finishReason", response.getResult().getMetadata().getFinishReason());
            }
        }
        return new TraceStep("model", "Model call: " + modelName, "ChatModel", startMs, durMs, detail);
    }

    private static TraceStep toolStep(ToolCallingObservationContext context, long startMs, long durMs) {
        String name = context.getToolDefinition().name();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", name);
        detail.put("args", abbreviate(context.getToolCallArguments()));
        detail.put("result", abbreviate(context.getToolCallResult()));
        return new TraceStep("tool", "Tool " + name, "@Tool", startMs, durMs, detail);
    }

    private static String traceId(Observation.Context context) {
        for (Observation.ContextView view = context.getParentObservation() == null
                ? null : context.getParentObservation().getContextView(); view != null; ) {
            if (view instanceof ChatClientObservationContext chatClient) {
                Object id = chatClient.getRequest().context().get(TraceCollector.TRACE_ID_PARAM);
                return id == null ? null : id.toString();
            }
            view = view instanceof Observation.Context parentContext && parentContext.getParentObservation() != null
                    ? parentContext.getParentObservation().getContextView() : null;
        }
        return null;
    }

    private static String abbreviate(String text) {
        if (text == null || text.length() <= MAX_TEXT) {
            return text;
        }
        return text.substring(0, MAX_TEXT) + "…";
    }
}
