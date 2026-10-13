package com.tommap.springaiapp.knowledge;

/** score is cosine similarity (1 = identical); page is null for Markdown sources. */
public record SearchHit(String source, Integer page, double score, String text) {
}
