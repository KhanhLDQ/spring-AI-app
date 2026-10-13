package com.tommap.springaiapp.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.tommap.springaiapp.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A real MCP client conversation over HTTP: initialize, list the tools, call one. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class McpServerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    private HttpResponse<String> post(String body, String sessionId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            request.header("Mcp-Session-Id", sessionId);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The reply is one JSON-RPC message, sent either as plain JSON or as a single server-sent event. */
    private static JsonNode result(HttpResponse<String> response) {
        String body = response.body().lines().filter(l -> l.startsWith("data:")).findFirst()
                .map(l -> l.substring(5).trim()).orElse(response.body());
        return JSON.readTree(body);
    }

    private String initialize() throws Exception {
        HttpResponse<String> init = post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26",
                "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""", null);
        assertThat(init.statusCode()).isEqualTo(200);
        assertThat(result(init).get("result").get("serverInfo").get("name").asString()).isEqualTo("kefir");
        String id = init.headers().firstValue("Mcp-Session-Id").orElse(null);
        post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", id);
        return id;
    }

    @Test
    void onlyTheReadOnlyToolsAreListed() throws Exception {
        String session = initialize();

        JsonNode tools = result(post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", session))
                .get("result").get("tools");

        assertThat(tools.valueStream().map(t -> t.get("name").asString()))
                .containsExactlyInAnyOrder("listProducts", "getProduct", "searchKnowledge");
    }

    @Test
    void aToolCallRunsTheSameCodeAsTheChat() throws Exception {
        String session = initialize();

        JsonNode products = result(post("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"listProducts","arguments":{}}}""",
                session)).get("result");
        JsonNode knowledge = result(post("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"searchKnowledge",
                "arguments":{"query":"Kefir mật ong để được bao lâu?"}}}""", session)).get("result");

        assertThat(products.get("content").get(0).get("text").asString()).contains("MAT-ONG-330");
        assertThat(knowledge.get("isError") == null || !knowledge.get("isError").asBoolean()).isTrue();
        assertThat(knowledge.get("content").get(0).get("text").asString()).contains("source");
    }
}
