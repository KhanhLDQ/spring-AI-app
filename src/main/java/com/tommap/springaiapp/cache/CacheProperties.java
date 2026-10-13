package com.tommap.springaiapp.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** similarityThreshold: cosine score (0 to 1) at which a new question counts as one already answered. */
@ConfigurationProperties("kefir.cache")
record CacheProperties(double similarityThreshold, Duration ttl) {
}
