import type { StepKind } from "../api/chat";

/** Java snippets shown under each step; they illustrate the concept, not the exact code of the app. */
export const HOW_SPRING_AI_DOES_IT: Record<StepKind, string> = {
  cache: "class SemanticCacheAdvisor\n    implements CallAdvisor, StreamAdvisor {\n  // embed → similaritySearch → return or continue the chain\n}",
  cachewrite: "// only cache turns without tool calls\nif (!response.hasToolCalls()) cache.add(question, answer);",
  memory: "MessageChatMemoryAdvisor\n    .builder(chatMemory).build()",
  rag: "new QuestionAnswerAdvisor(vectorStore)",
  model: "chatClient.prompt()\n    .user(question)\n    .stream().content();   // Flux<String> → SSE",
  tool: '@Tool(description = "Create a new order")\nOrder createOrder(String product, int qty,\n                  ToolContext ctx) { … }',
};
