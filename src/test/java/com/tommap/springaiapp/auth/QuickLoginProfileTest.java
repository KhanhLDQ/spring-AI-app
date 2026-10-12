package com.tommap.springaiapp.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class QuickLoginProfileTest {

    @Autowired
    MockMvc mvc;

    @Test
    void quickLoginDoesNotExistOutsideDemoProfiles() throws Exception {
        mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"an\"}"))
                .andExpect(status().isNotFound());
    }
}
