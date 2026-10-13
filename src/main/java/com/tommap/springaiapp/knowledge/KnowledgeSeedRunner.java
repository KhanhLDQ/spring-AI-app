package com.tommap.springaiapp.knowledge;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.seedOnStart() || !knowledge.isEmpty()) {
            return;
        }
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:knowledge/*.md");
        Arrays.sort(files, Comparator.comparing(Resource::getFilename));
        for (Resource file : files) {
            knowledge.ingest(file.getFilename(), file.getContentAsByteArray());
        }
    }
}
