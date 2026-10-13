package com.tommap.springaiapp.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;

record KnowledgeDocView(long id, String filename, Instant uploadedAt, int chunkCount) {
}

/** Milliseconds per ETL stage plus the chunk count; the Knowledge screen draws these as a pipeline. */
record EtlTimings(long readMs, long splitMs, long embedMs, long storeMs, int chunks) {
}

record IngestResult(long id, String filename, int chunkCount, EtlTimings etl) {
}

/** score is cosine similarity (1 = identical); page is null for Markdown sources. */
record SearchHit(String source, Integer page, double score, String text) {
}

/** The chunk size in force, the default it resets to, and what the knowledge base holds at that size. */
record ChunkingView(int chunkTokens, int defaultChunkTokens, int documents, int chunks) {
}

record ChunkingRequest(@Min(50) @Max(1000) int chunkTokens) {
}
