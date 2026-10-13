package com.tommap.springaiapp.knowledge;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Loads the bundled shop documents on first start; the demo PDF is deliberately not among them. */
@Component
class KnowledgeSeedRunner implements ApplicationRunner {

    private final KnowledgeService knowledge;
    private final KnowledgeProperties properties;

    KnowledgeSeedRunner(KnowledgeService knowledge, KnowledgeProperties properties) {
        this.knowledge = knowledge;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.seedOnStart() && knowledge.isEmpty()) {
            knowledge.reseed();
        }
    }
}
