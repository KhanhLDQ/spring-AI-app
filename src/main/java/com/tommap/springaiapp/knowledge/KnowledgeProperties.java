package com.tommap.springaiapp.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Upload limits and chunk size (the chunk count in the ETL pipeline depends on chunkTokens), plus how chat
 * retrieves from the knowledge base. seedOnStart loads the bundled shop documents when the knowledge base is
 * empty; turn it off to start without calling the embedding API.
 */
@ConfigurationProperties("kefir.knowledge")
record KnowledgeProperties(DataSize maxFileSize, int maxPages, int chunkTokens, boolean seedOnStart, Rag rag,
                         Router router) {

    /** topK chunks at or above similarityThreshold (cosine, 0 to 1) are handed to the model as context. */
    record Rag(int topK, double similarityThreshold) {
    }

    /**
     * A message whose best score against the order examples reaches similarityThreshold, and beats its best
     * score against the knowledge examples by at least margin, skips retrieval.
     */
    record Router(double similarityThreshold, double margin) {
    }
}
