package com.tommap.springaiapp.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.tommap.springaiapp.TestcontainersConfiguration;
import com.tommap.springaiapp.order.OrderSeedService;
import com.tommap.springaiapp.order.OrderService;
import com.tommap.springaiapp.order.OrderStatus;
import com.tommap.springaiapp.order.OrderView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The tool loop end to end with the scripted model: no network, no cost. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ToolCallingIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    OrderSeedService seed;
    @Autowired
    OrderService orders;
    @Autowired
    JdbcTemplate jdbc;

    private ChatTestClient chat;

    @BeforeEach
    void setUp() {
        seed.reseed();
        chat = new ChatTestClient(mvc);
    }

    @Test
    void orderingThroughChatCreatesAnOrderForTheCaller() throws Exception {
        var reply = chat.chat(chat.login("an"), "order 5 honey kefir");

        assertThat(reply.toolSteps()).singleElement()
                .satisfies(step -> assertThat(step.get("detail").get("name").asString()).isEqualTo("createOrder"));
        assertThat(reply.answer()).startsWith("Done. Order #");
        assertThat(orders.listMine("an")).hasSize(5).first().satisfies(o -> {
            assertThat(o.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(o.totalAmount()).isEqualTo(5 * 38_000);
        });
        assertThat(orders.listMine("admin")).hasSize(2);
        assertThat(stock("MAT-ONG-330")).isEqualTo(19);
    }

    @Test
    void refusalFromTheToolIsRelayedAndNothingIsOrdered() throws Exception {
        var reply = chat.chat(chat.login("an"), "order 500 honey kefir");

        assertThat(reply.answer()).contains("maximum is 20").contains("24 in stock");
        assertThat(orders.listMine("an")).hasSize(4);
        assertThat(stock("MAT-ONG-330")).isEqualTo(24);
    }

    @Test
    void aCustomerCannotUseAdminTools() throws Exception {
        var reply = chat.chat(chat.login("an"), "show all orders");

        assertThat(reply.toolSteps()).singleElement()
                .satisfies(step -> assertThat(step.get("detail").get("name").asString()).isEqualTo("listAllOrders"));
        assertThat(reply.answer()).contains("Only the shop administrator");
        assertThat(reply.body()).doesNotContain("Kefir Nguyên Bản");
    }

    @Test
    void anAdminCanListEveryonesOrders() throws Exception {
        var reply = chat.chat(chat.login("admin"), "show all orders");

        assertThat(reply.answer()).isEqualTo("I found 6 item(s). Details are in the Inspector.");
    }

    @Test
    void cancellingThroughChatReturnsStockAndOnlyWorksOnYourOwnPendingOrders() throws Exception {
        String token = chat.login("an");
        chat.chat(token, "order 4 honey kefir");
        OrderView fresh = orders.listMine("an").getFirst();
        assertThat(stock("MAT-ONG-330")).isEqualTo(20);

        assertThat(chat.chat(token, "cancel order #" + fresh.id()).answer())
                .isEqualTo("Done. Order #" + fresh.id() + " is CANCELLED, total 152000 VND.");
        assertThat(stock("MAT-ONG-330")).isEqualTo(24);

        assertThat(chat.chat(token, "cancel order #1017").answer()).contains("APPROVED");
        assertThat(chat.chat(token, "cancel order #1031").answer()).contains("Order #1031 was not found");
        assertThat(orders.listAll(OrderStatus.PENDING)).extracting(OrderView::id).contains(1031L, 1042L);
    }

    @Test
    void changingTheQuantityThroughChatAdjustsStock() throws Exception {
        var reply = chat.chat(chat.login("an"), "change order #1042 to 3");

        assertThat(reply.answer()).isEqualTo("Done. Order #1042 is PENDING, total 114000 VND.");
        assertThat(List.of(stock("MAT-ONG-330"))).containsExactly(26);
    }

    private int stock(String sku) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE sku = ?", Integer.class, sku);
    }
}
