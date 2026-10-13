package com.tommap.springaiapp.knowledge;

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
