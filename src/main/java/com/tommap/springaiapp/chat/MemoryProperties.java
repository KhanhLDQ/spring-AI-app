package com.tommap.springaiapp.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** How many of the latest messages of a conversation are sent back to the model on every turn. */
@ConfigurationProperties("kefir.memory")
record MemoryProperties(int windowSize) {
}
