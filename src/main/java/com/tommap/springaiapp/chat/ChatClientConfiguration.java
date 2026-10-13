package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.cache.SemanticCacheAdvisor;
import com.tommap.springaiapp.knowledge.KnowledgeAdvisor;
import com.tommap.springaiapp.trace.TraceCollector;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PricingProperties.class, MemoryProperties.class})
class ChatClientConfiguration {

    /**
     * The tool manager must carry the observation registry, otherwise tool calls never reach the Inspector.
     */
    /** Defined here, not left to auto-configuration, so the window size is a setting we can show in the Inspector. */
    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, MemoryProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository).maxMessages(properties.windowSize()).build();
    }

    /** Outermost of the stateful advisors: it saves the question as typed, before retrieval wraps it in context. */
    @Bean
    ConversationMemoryAdvisor memoryAdvisor(ChatMemory chatMemory, TraceCollector traces, MemoryProperties properties) {
        return new ConversationMemoryAdvisor(MessageChatMemoryAdvisor.builder(chatMemory).build(), chatMemory, traces,
                properties.windowSize());
    }

    @Bean
    ChatClient chatClient(ChatModel chatModel, ObservationRegistry observationRegistry,
                          ToolCallingManager toolCallingManager, SemanticCacheAdvisor cacheAdvisor,
                          ConversationMemoryAdvisor memoryAdvisor, KnowledgeAdvisor knowledgeAdvisor) {
        return ChatClient.builder(chatModel, observationRegistry, null, null,
                        ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
                .defaultAdvisors(cacheAdvisor, memoryAdvisor, knowledgeAdvisor)
                .build();
    }
}
