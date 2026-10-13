package com.tommap.springaiapp.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Upload limits and chunk size (the chunk count in the ETL pipeline depends on chunkTokens).
 * seedOnStart loads the bundled shop documents when the knowledge base is empty; turn it off to start
 * without calling the embedding API.
 */
@ConfigurationProperties("kefir.knowledge")
record KnowledgeProperties(DataSize maxFileSize, int maxPages, int chunkTokens, boolean seedOnStart) {
}
