package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.order.RevenueService;
import com.tommap.springaiapp.order.RevenueSummary;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Structured output: the model gets the revenue figures (computed in plain Java, not by the model) and must fill
 * in a {@link RevenueBrief}. A plain ChatClient is used on purpose, without the chat advisors or tools.
 */
@Service
public class RevenueBriefService {

    private static final Logger log = LoggerFactory.getLogger(RevenueBriefService.class);

    private static final String SYSTEM = """
            You write short revenue briefs for the owner of a small kefir shop. Use only the figures you are given \
            and never invent numbers. Write in Vietnamese. headline: one sentence. highlights: two to four short \
            points. suggestion: one practical next step.""";

    /** The wording the scripted mock model recognises; a real model just reads it as the instruction. */
    public static final String REQUEST = "Write a revenue brief for these figures (amounts in VND):\n";

    /** What the endpoint returns: the typed brief plus what a talk needs to show how it was produced. */
    public record Result(RevenueBrief brief, RevenueSummary summary, long tokensIn, long tokensOut, BigDecimal cost,
                         long ms, String schema, String raw) {
    }

    private final ChatModel chatModel;
    private final RevenueService revenue;
    private final UsageLedger usage;
    private final JsonMapper json;

    RevenueBriefService(ChatModel chatModel, RevenueService revenue, UsageLedger usage, JsonMapper json) {
        this.chatModel = chatModel;
        this.revenue = revenue;
        this.usage = usage;
        this.json = json;
    }

    Result brief(String fromDate, String toDate) {
        if (usage.exhausted()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "The demo budget is spent, so the model is paused.");
        }
        RevenueSummary summary = revenue.summarize(fromDate, toDate);
        BeanOutputConverter<RevenueBrief> converter = new BeanOutputConverter<>(RevenueBrief.class);
        long start = System.nanoTime();
        ResponseEntity<ChatResponse, RevenueBrief> answer;
        try {
            answer = ChatClient.builder(chatModel).build().prompt()
                    .system(SYSTEM)
                    .user(REQUEST + json.writeValueAsString(summary))
                    .call()
                    .responseEntity(converter);
        } catch (RuntimeException e) {
            log.warn("Revenue brief failed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The model did not return a usable brief. Please try again.");
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        ChatResponse response = answer.response();
        Usage tokens = response.getMetadata().getUsage();
        long tokensIn = tokens.getPromptTokens() == null ? 0 : tokens.getPromptTokens();
        long tokensOut = tokens.getCompletionTokens() == null ? 0 : tokens.getCompletionTokens();
        BigDecimal cost = usage.cost(tokensIn, tokensOut);
        if (tokensIn + tokensOut > 0) {
            usage.record(tokensIn, tokensOut, cost);
        }
        return new Result(answer.entity(), summary, tokensIn, tokensOut, cost, ms, converter.getJsonSchema(),
                response.getResult().getOutput().getText());
    }
}
