package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.trace.TraceCollector;
import com.tommap.springaiapp.trace.TraceStep;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

/**
 * MessageChatMemoryAdvisor plus an Inspector step saying how much history is loaded. The advisor emits no
 * observation, and it must keep its own streaming logic (it saves the answer once the stream is complete), so it
 * is called through unchanged; the step comes from reading the same window of messages just before it runs.
 */
final class ConversationMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    private final MessageChatMemoryAdvisor memory;
    private final ChatMemory chatMemory;
    private final TraceCollector traces;
    private final int windowSize;

    ConversationMemoryAdvisor(MessageChatMemoryAdvisor memory, ChatMemory chatMemory, TraceCollector traces,
                              int windowSize) {
        this.memory = memory;
        this.chatMemory = chatMemory;
        this.traces = traces;
        this.windowSize = windowSize;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        recordStep(request);
        return memory.adviseCall(request, chain);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        recordStep(request);
        return memory.adviseStream(request, chain);
    }

    @Override
    public String getName() {
        return memory.getName();
    }

    @Override
    public int getOrder() {
        return memory.getOrder();
    }

    private void recordStep(ChatClientRequest request) {
        if (!(request.context().get(TraceCollector.TRACE_ID_PARAM) instanceof String traceId)
                || !(request.context().get(ChatMemory.CONVERSATION_ID) instanceof String conversationId)) {
            return;
        }
        long start = System.nanoTime();
        int loaded = chatMemory.get(conversationId).size();
        long end = System.nanoTime();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("messagesLoaded", loaded);
        detail.put("windowSize", windowSize);
        traces.record(traceId, new TraceStep("memory", "Chat memory: " + loaded + " earlier message(s)",
                "MessageChatMemoryAdvisor", (start - traces.startNanos(traceId)) / 1_000_000,
                (end - start) / 1_000_000, detail));
    }
}
