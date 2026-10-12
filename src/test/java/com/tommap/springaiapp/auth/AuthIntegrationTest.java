package com.tommap.springaiapp.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void quickLoginIssuesTokenThatIdentifiesTheUser() throws Exception {
        String token = login("an");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("an")))
                .andExpect(jsonPath("$.role", is("USER")));
    }

    @Test
    void quickLoginReturnsExpiryOfTwentyFourHours() throws Exception {
        mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.expiresIn", is(86400)))
                .andExpect(jsonPath("$.user.role", is("ADMIN")));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankUsernameIsRejectedAsProblemDetail() throws Exception {
        mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorizedProblemDetail() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = login("an");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReachAdminEndpointsButAdminPassesTheGate() throws Exception {
        mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + login("an")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + login("admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void mcpEndpointIsClosed() throws Exception {
        mvc.perform(get("/mcp")).andExpect(status().isUnauthorized());
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = JsonMapper.builder().build().readTree(body);
        return json.get("token").asString();
    }
}
