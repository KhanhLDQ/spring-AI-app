package com.tommap.springaiapp.mock;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("mock")
class MockAiConfiguration {

    @Bean
    MockChatModel mockChatModel(ObservationRegistry observationRegistry) {
        return new MockChatModel(observationRegistry);
    }

    @Bean
    MockEmbeddingModel mockEmbeddingModel() {
        return new MockEmbeddingModel();
    }
}
