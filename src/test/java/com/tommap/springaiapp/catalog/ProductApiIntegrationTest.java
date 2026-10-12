package com.tommap.springaiapp.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void productsRequireALogin() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsTheWholeMenuInStableOrderWithPriceStockAndImage() throws Exception {
        mvc.perform(get("/api/products").header("Authorization", "Bearer " + login("an")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].sku", is("HY-LAP-250")))
                .andExpect(jsonPath("$[0].name", is("Hy Lạp Kefir 250g")))
                .andExpect(jsonPath("$[0].priceVnd", is(90000)))
                .andExpect(jsonPath("$[0].image", is("/images/huong-vi/hy-lap.webp")))
                .andExpect(jsonPath("$[6].sku", is("MAT-ONG-330")))
                .andExpect(jsonPath("$[6].shelfLifeDays", is(3)));
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(body).get("token").asString();
    }
}
