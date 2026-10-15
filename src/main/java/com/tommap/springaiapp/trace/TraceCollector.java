package com.tommap.springaiapp.trace;

import java.time.Duration;
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

    /** A model or tool observation of this trace has started; its step arrives when it stops. */
    public void opened(String traceId) {
        Trace trace = traces.get(traceId);
        if (trace != null) {
            synchronized (trace) {
                trace.open[0]++;
            }
        }
    }

    public void closed(String traceId) {
        Trace trace = traces.get(traceId);
        if (trace != null) {
            synchronized (trace) {
                trace.open[0]--;
                trace.notifyAll();
            }
        }
    }

    /**
     * Blocks until every observation of the trace has stopped, or the wait runs out. A streaming model stops its
     * observation only after the last token has gone downstream, so without this wait the turn could be summed up
     * before the model step (and its token count) is recorded.
     */
    public void awaitObservations(String traceId, Duration max) {
        Trace trace = traces.get(traceId);
        if (trace == null) {
            return;
        }
        long deadline = System.nanoTime() + max.toNanos();
        synchronized (trace) {
            long left;
            while (trace.open[0] > 0 && (left = deadline - System.nanoTime()) > 0) {
                try {
                    trace.wait(Math.max(1, left / 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Removes the trace and returns its steps ordered by start time. */
    public List<TraceStep> finish(String traceId) {
        Trace trace = traces.remove(traceId);
        if (trace == null) {
            return List.of();
        }
        return trace.steps.stream().sorted(Comparator.comparingLong(TraceStep::startMs)).toList();
    }

    /** {@code open}: observations started but not yet stopped, guarded by the trace's own monitor. */
    private record Trace(long startNanos, List<TraceStep> steps, int[] open) {

        Trace(long startNanos) {
            this(startNanos, new CopyOnWriteArrayList<>(), new int[1]);
        }
    }
}
