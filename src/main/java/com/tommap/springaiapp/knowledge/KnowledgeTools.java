package com.tommap.springaiapp.knowledge;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Read-only knowledge lookup for MCP clients. The chat does not use it: there retrieval is an advisor that runs
 * on every message, here an external model decides when to call it.
 */
@Component
public class KnowledgeTools {

    private final KnowledgeService knowledge;
    private final KnowledgeAdvisor rag;

    KnowledgeTools(KnowledgeService knowledge, KnowledgeAdvisor rag) {
        this.knowledge = knowledge;
        this.rag = rag;
    }

    @Tool(description = """
            Search the Mayiu Kefir shop knowledge base (products, shelf life, how to drink and store kefir, \
            delivery, promotions) and return the most relevant passages with their source file and similarity \
            score. An empty list means the shop has no information on the question.""")
    public List<SearchHit> searchKnowledge(
            @ToolParam(description = "The question or keywords, in Vietnamese or English") String query) {
        return knowledge.search(query).stream().filter(hit -> hit.score() >= rag.threshold()).limit(3).toList();
    }
}
