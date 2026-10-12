package com.tommap.springaiapp.trace;

import java.util.Map;

/** One Inspector row: what Spring AI did, when, and for how long. */
public record TraceStep(String kind, String title, String concept, long startMs, long durMs,
                        Map<String, Object> detail) {
}
