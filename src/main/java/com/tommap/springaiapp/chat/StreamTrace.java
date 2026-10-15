package com.tommap.springaiapp.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * What actually came down the ChatClient stream of one turn, measured as each chunk passed through: when the
 * first chunk arrived, how many there were, and which chunk carried the finish reason that ended the stream.
 * Times are milliseconds from the start of the turn, the same clock as the trace steps.
 */
final class StreamTrace {

    private final long startNanos;
    private int chunks;
    private int textChunks;
    private long firstChunkMs = -1;
    private long lastChunkMs = -1;
    private final List<Map<String, Object>> finishes = new ArrayList<>();

    StreamTrace(long startNanos) {
        this.startNanos = startNanos;
    }

    synchronized void onChunk(ChatResponse response) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        chunks++;
        if (firstChunkMs < 0) {
            firstChunkMs = ms;
        }
        lastChunkMs = ms;
        Generation generation = response.getResult();
        if (generation == null) {
            return;
        }
        String text = generation.getOutput().getText();
        if (text != null && !text.isEmpty()) {
            textChunks++;
        }
        String reason = generation.getMetadata().getFinishReason();
        if (reason != null && !reason.isBlank()) {
            Map<String, Object> finish = new LinkedHashMap<>();
            finish.put("chunk", chunks);
            finish.put("ms", ms);
            finish.put("reason", reason);
            finishes.add(finish);
        }
    }

    /** Empty when nothing was streamed (a cache hit answers without the model). */
    synchronized Map<String, Object> view() {
        if (chunks == 0) {
            return Map.of();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("chunks", chunks);
        view.put("textChunks", textChunks);
        view.put("firstChunkMs", firstChunkMs);
        view.put("lastChunkMs", lastChunkMs);
        view.put("finishes", List.copyOf(finishes));
        return view;
    }
}
