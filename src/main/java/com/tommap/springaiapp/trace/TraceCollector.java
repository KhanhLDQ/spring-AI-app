package com.tommap.springaiapp.trace;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Gathers the steps of in-flight chat turns. Keyed by trace id instead of a ThreadLocal because
 * model and tool observations run on reactor threads, not the request thread.
 */
@Component
public class TraceCollector {

    /** Advisor-context parameter that ties a chat client call to its trace. */
    public static final String TRACE_ID_PARAM = "kefir.traceId";

    private final Map<String, Trace> traces = new ConcurrentHashMap<>();

    public void begin(String traceId) {
        traces.put(traceId, new Trace(System.nanoTime()));
    }

    public long startNanos(String traceId) {
        Trace trace = traces.get(traceId);
        return trace == null ? 0 : trace.startNanos;
    }

    public void record(String traceId, TraceStep step) {
        Trace trace = traces.get(traceId);
        if (trace != null) {
            trace.steps.add(step);
        }
    }

    /** The steps recorded so far, without ending the trace; lets an advisor decide from what the turn did. */
    public List<TraceStep> snapshot(String traceId) {
        Trace trace = traces.get(traceId);
        return trace == null ? List.of() : List.copyOf(trace.steps);
    }

    /** Removes the trace and returns its steps ordered by start time. */
    public List<TraceStep> finish(String traceId) {
        Trace trace = traces.remove(traceId);
        if (trace == null) {
            return List.of();
        }
        return trace.steps.stream().sorted(Comparator.comparingLong(TraceStep::startMs)).toList();
    }

    private record Trace(long startNanos, List<TraceStep> steps) {

        Trace(long startNanos) {
            this(startNanos, new CopyOnWriteArrayList<>());
        }
    }
}
