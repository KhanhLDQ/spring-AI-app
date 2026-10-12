package com.tommap.springaiapp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    OrderSeedService seed;
    @Autowired
    JdbcTemplate jdbc;

    private String user;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        seed.reseed();
        user = "Bearer " + login("an");
        admin = "Bearer " + login("admin");
    }

    @Test
    void mineReturnsOnlyTheCallersOrdersNewestFirst() throws Exception {
        mvc.perform(get("/api/orders/mine").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(1042, 1017, 1009, 1003)))
                .andExpect(jsonPath("$[0].username", is("an")))
                .andExpect(jsonPath("$[0].status", is("PENDING")))
                .andExpect(jsonPath("$[0].totalAmount", is(190000)))
                .andExpect(jsonPath("$[0].items[0].product", is("Kefir Mật Ong")))
                .andExpect(jsonPath("$[0].items[0].size", is("330ml")))
                .andExpect(jsonPath("$[0].items[0].quantity", is(5)))
                .andExpect(jsonPath("$[0].items[0].unitPrice", is(38000)));
    }

    @Test
    void ordersRequireALogin() throws Exception {
        mvc.perform(get("/api/orders/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsAreClosedToCustomers() throws Exception {
        mvc.perform(get("/api/admin/orders").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/orders/1042/approve").header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListsEveryoneAndFiltersByStatus() throws Exception {
        mvc.perform(get("/api/admin/orders").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/api/admin/orders?status=PENDING").header("Authorization", admin))
                .andExpect(jsonPath("$[*].id", contains(1042, 1031)));
        mvc.perform(get("/api/admin/orders?status=BOGUS").header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveMovesAPendingOrderAndASecondApproveConflicts() throws Exception {
        mvc.perform(post("/api/admin/orders/1042/approve").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("APPROVED")));

        mvc.perform(post("/api/admin/orders/1042/approve").header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.detail").value(containsString("APPROVED")));
    }

    @Test
    void rejectReturnsStockAndUnknownOrdersAre404() throws Exception {
        int before = stock("MAT-ONG-330");

        mvc.perform(post("/api/admin/orders/1042/reject").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("REJECTED")));

        assertThat(stock("MAT-ONG-330")).isEqualTo(before + 5);
        mvc.perform(post("/api/admin/orders/9999/approve").header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    private int stock(String sku) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE sku = ?", Integer.class, sku);
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/quick-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(body).get("token").asString();
    }
}
