package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "kefir.cache.ttl=1s")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SemanticCacheTtlIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anEntryOlderThanTheTimeToLiveIsNoLongerServed() throws Exception {
        ChatTestClient chat = new ChatTestClient(mvc);
        String admin = chat.login("admin");
        String user = chat.login("an");
        mvc.perform(delete("/api/admin/knowledge").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(multipart("/api/admin/knowledge")
                        .file(new MockMultipartFile("file", "shelf-life.md", "application/octet-stream",
                                "## Shelf life\nKefir Mật Ong để được dưới 3 ngày.".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        String question = "Kefir mật ong để được bao lâu?";

        chat.chat(user, question);
        assertThat(chat.chat(user, question).data("done").getFirst().get("cache").asString()).isEqualTo("HIT");

        Thread.sleep(1500);

        assertThat(chat.chat(user, question).data("done").getFirst().get("cache").asString()).isEqualTo("MISS");
    }
}
