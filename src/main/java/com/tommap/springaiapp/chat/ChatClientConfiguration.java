package com.tommap.springaiapp.chat;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PricingProperties.class)
class ChatClientConfiguration {

    /**
     * The tool manager must carry the observation registry, otherwise tool calls never reach the Inspector.
     */
    @Bean
    ChatClient chatClient(ChatModel chatModel, ObservationRegistry observationRegistry,
                          ToolCallingManager toolCallingManager) {
        return ChatClient.builder(chatModel, observationRegistry, null, null,
                ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager)).build();
    }
}
