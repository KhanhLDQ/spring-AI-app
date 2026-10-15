import type { StepKind, TraceStep } from "../api/chat";

/**
 * Excerpts of this app's own code behind each step, trimmed with "…"; the first comment names the file. Keep them
 * in step with the Java sources: the audience reads them as the real thing.
 */
const BY_KIND: Record<StepKind, string> = {
  cache: `// cache/SemanticCacheAdvisor.java (custom CallAdvisor + StreamAdvisor, outermost)
Optional<SemanticCache.Match> nearest = cache.nearest(turn.question(), turn.role());  // same role, within TTL
boolean hit = nearest.isPresent() && score >= threshold;
…
return Mono.fromCallable(() -> lookup(turn)).subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(lookup -> lookup.hit()
                ? Flux.fromIterable(chunks(lookup.answer())).map(text -> responseOf(request, text))
                : new ChatClientMessageAggregator().aggregateChatClientResponse(
                        chain.nextStream(request), response -> afterMiss(turn, …)));`,
  cachewrite: `// cache/SemanticCacheAdvisor.java: whyNotReusable(), null means store
if (steps.stream().anyMatch(s -> "tool".equals(s.kind()))) {
    return "the turn called a tool";
}
if (steps.stream().noneMatch(s -> "rag".equals(s.kind()) && hasChunks(s))) {
    return "no knowledge was used";
}
if (earlierMessages > 0) {
    return "depends on earlier messages";
}
return null;   // → cache.store(question, answer, role, sources)`,
  memory: `// chat/ChatClientConfiguration.java
MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository).maxMessages(properties.windowSize()).build();  // JDBC, 20
new ConversationMemoryAdvisor(MessageChatMemoryAdvisor.builder(chatMemory).build(), …);

// chat/ConversationMemoryAdvisor.java: records this step, then runs the original advisor unchanged
recordStep(request);
return memory.adviseStream(request, chain);

// chat/ChatService.java: conversation id = user.username() + ":" + conversationId`,
  route: `// knowledge/QueryRouter.java (custom)
float[] query = embeddings.embed(message);
double transactional = best(query, transactionalVectors);   // router/transactional.txt
double knowledge = best(query, knowledgeVectors);           // router/knowledge.txt
boolean skip = transactional >= threshold && transactional - knowledge >= margin;

// knowledge/KnowledgeAdvisor.java: skip → no retrieval, add TOOL_REMINDER to the message`,
  rag: `// knowledge/KnowledgeAdvisor.java
QuestionAnswerAdvisor.builder(store)
        .searchRequest(SearchRequest.builder().topK(topK).similarityThreshold(value).build())
        .promptTemplate(new PromptTemplate(RagPrompt.TEMPLATE))
        .order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 50)
        .build();
…
ChatClientRequest augmented = retrieval.before(request, chain);   // chunks → prompt`,
  model: `// chat/ChatService.java
chatClient.prompt()
        .system(SystemPrompts.forRole(user.role(), ShopTime.today(clock), !off.contains(AdvisorSwitch.TOOLS)))
        .user(message)
        .advisors(a -> a.param(TraceCollector.TRACE_ID_PARAM, traceId) …
                .param(AdvisorSwitch.OFF_PARAM, Set.copyOf(off)))   // the chat screen's switches
        .tools(catalogTools, orderTools)          // left out when Tools is switched off
        .toolContext(toolContext)
        .stream().chatResponse()          // Flux<ChatResponse> → ChatEvent.Token → SSE
…
// chat/ChatClientConfiguration.java
ChatClient.builder(chatModel, observationRegistry, null, null,
                ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager))
        .defaultAdvisors(cacheAdvisor, memoryAdvisor, knowledgeAdvisor);`,
  tool: `// order/OrderTools.java, catalog/CatalogTools.java
@Tool(description = "…")
public OrderView createOrder(@ToolParam(description = "…") String product,
                             @ToolParam(description = "…") int quantity,
                             ToolContext context) { … }`,
};

/** The real method behind each tool the chat can call. */
const BY_TOOL: Record<string, string> = {
  listProducts: `// catalog/CatalogTools.java
@Tool(description = """
        List every Mayiu Kefir product with its SKU, name (flavour and size), price in VND, current stock, \\
        shelf life in days and description. …""")
public List<ProductView> listProducts() {
    return catalog.list();
}`,
  getProduct: `// catalog/CatalogTools.java
@Tool(description = "Get one product by SKU or by name (for example 'Kefir Mật Ong 330ml'): price in VND and stock.")
public ProductView getProduct(
        @ToolParam(description = "SKU or product name including the size") String product) {
    return catalog.get(product);
}`,
  createOrder: `// order/OrderTools.java
@Tool(description = """
        Place an order for the current user right away, without asking for confirmation. The order starts as \\
        PENDING until an administrator approves it. At most 20 bottles per order and never more than the \\
        stock. Prices are in VND.""")
public OrderView createOrder(
        @ToolParam(description = "SKU or product name including the size, e.g. MAT-ONG-330") String product,
        @ToolParam(description = "Number of bottles, 1 to 20") int quantity,
        ToolContext context) {
    return orders.create(ToolCaller.from(context).username(), product, quantity);
}`,
  updateOrderQuantity: `// order/OrderTools.java
@Tool(description = "Change the quantity of one of the current user's own orders. Only PENDING orders can change.")
public OrderView updateOrderQuantity(
        @ToolParam(description = "Order id, e.g. 1042") long orderId,
        @ToolParam(description = "New number of bottles, 1 to 20") int newQuantity,
        ToolContext context) {
    return orders.updateQuantity(ToolCaller.from(context).username(), orderId, newQuantity);
}`,
  cancelOrder: `// order/OrderTools.java
@Tool(description = "Cancel one of the current user's own orders and return its bottles to stock. "
        + "Only PENDING orders can be cancelled.")
public OrderView cancelOrder(
        @ToolParam(description = "Order id, e.g. 1042") long orderId,
        ToolContext context) {
    return orders.cancel(ToolCaller.from(context).username(), orderId);
}`,
  listMyOrders: `// order/OrderTools.java
@Tool(description = "List the current user's own orders, newest first, with status and total in VND.")
public List<OrderView> listMyOrders(ToolContext context) {
    return orders.listMine(ToolCaller.from(context).username());
}`,
  listAllOrders: `// order/OrderTools.java
@Tool(description = "ADMIN ONLY. List orders of all customers, newest first, optionally only one status.")
public List<OrderView> listAllOrders(
        @ToolParam(description = "PENDING, APPROVED, REJECTED or CANCELLED; leave empty for all", required = false)
        String status,
        ToolContext context) {
    ToolCaller.from(context).requireAdmin("list the orders of all customers");
    return orders.listAll(parseStatus(status));
}`,
  approveOrder: `// order/OrderTools.java
@Tool(description = "ADMIN ONLY. Approve a PENDING order of any customer.")
public OrderView approveOrder(
        @ToolParam(description = "Order id, e.g. 1042") long orderId,
        ToolContext context) {
    ToolCaller.from(context).requireAdmin("approve orders");
    return orders.approve(orderId);
}`,
  getRevenueSummary: `// order/OrderTools.java
@Tool(description = """
        ADMIN ONLY. Revenue of approved orders between two dates (both included), … \\
        Without dates it covers the last 60 days.""")
public RevenueSummary getRevenueSummary(
        @ToolParam(description = "First day as YYYY-MM-DD; …", required = false) String fromDate,
        @ToolParam(description = "Last day as YYYY-MM-DD; leave empty for today", required = false) String toDate,
        ToolContext context) {
    ToolCaller.from(context).requireAdmin("see revenue");   // USER → AdminOnlyException, read back by the model
    return revenue.summarize(fromDate, toDate);
}`,
};

export function howSpringAiDoesIt(step: TraceStep): string {
  if (step.kind === "tool" && typeof step.detail.name === "string" && BY_TOOL[step.detail.name]) {
    return BY_TOOL[step.detail.name];
  }
  return BY_KIND[step.kind];
}
