package com.tommap.springaiapp.mcp;

import com.tommap.springaiapp.catalog.CatalogTools;
import com.tommap.springaiapp.knowledge.KnowledgeTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The MCP server exposes the same {@code @Tool} methods the chat uses, but only the read-only ones: MCP calls do
 * not go through the User/Admin login, so nothing that places orders or shows revenue is registered here.
 */
@Configuration(proxyBeanMethods = false)
class McpConfiguration {

    @Bean
    ToolCallbackProvider mcpTools(CatalogTools catalog, KnowledgeTools knowledge) {
        return MethodToolCallbackProvider.builder().toolObjects(catalog, knowledge).build();
    }
}
