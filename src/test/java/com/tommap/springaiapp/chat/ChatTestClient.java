package com.tommap.springaiapp.chat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Drives the chat endpoints like the frontend does: log in, send a message, read the SSE events. */
public final class ChatTestClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;

    public ChatTestClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    public String login(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(response).get("token").asString();
    }

    public Reply chat(String token, String message) throws Exception {
        return chat(token, UUID.randomUUID(), message);
    }

    Reply chat(String token, UUID conversationId, String message) throws Exception {
        MvcResult started = mvc.perform(post("/api/chat/stream").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(conversationId, message)))
                .andExpect(request().asyncStarted()).andReturn();
        // SSE is always UTF-8, but the content type carries no charset and MockMvc would decode as ISO-8859-1.
        String body = mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Reply(body);
    }

    static String requestJson(String message) {
        return requestJson(UUID.randomUUID(), message);
    }

    static String requestJson(UUID conversationId, String message) {
        return JSON.createObjectNode().put("conversationId", conversationId.toString())
                .put("message", message).toString();
    }

    /** The parsed event stream of one chat turn. */
    public record Reply(String body) {

        List<String> eventNames() {
            return body.lines().filter(l -> l.startsWith("event:")).map(l -> l.substring(6).trim()).toList();
        }

        List<JsonNode> data(String event) {
            List<String> lines = body.lines().toList();
            return IntStream.range(0, lines.size())
                    .filter(i -> lines.get(i).equals("event:" + event))
                    .mapToObj(i -> JSON.readTree(lines.get(i + 1).substring("data:".length())))
                    .toList();
        }

        String answer() {
            return data("token").stream().map(d -> d.get("text").asString()).reduce("", String::concat);
        }

        List<JsonNode> toolSteps() {
            return data("step").stream().filter(s -> "tool".equals(s.get("kind").asString())).toList();
        }
    }
}
