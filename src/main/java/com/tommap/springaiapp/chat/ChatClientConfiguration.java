package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.cache.SemanticCacheAdvisor;
import com.tommap.springaiapp.knowledge.KnowledgeAdvisor;
import com.tommap.springaiapp.trace.TraceCollector;
import com.anthropic.models.messages.OutputConfig;
import io.micrometer.observation.ObservationRegistry;
import java.util.Locale;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PricingProperties.class, MemoryProperties.class, BudgetProperties.class})
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
                          ConversationMemoryAdvisor memoryAdvisor, KnowledgeAdvisor knowledgeAdvisor,
                          @Value("${kefir.model.effort:}") String effort) {
        ChatClient.Builder builder = ChatClient.builder(chatModel, observationRegistry, null, null,
                        ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
                .defaultAdvisors(cacheAdvisor, memoryAdvisor, knowledgeAdvisor);
        // Sonnet 5.5 thinks at "high" effort by default, which is slow and billed as output; a shop assistant does not need it.
        if (!effort.isBlank() && chatModel.getOptions() instanceof AnthropicChatOptions options) {
            builder.defaultOptions(options.mutate()
                    .effort(OutputConfig.Effort.Companion.of(effort.toLowerCase(Locale.ROOT))));
        }
        return builder.build();
    }
}
