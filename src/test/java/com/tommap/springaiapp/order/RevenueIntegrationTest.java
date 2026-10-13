package com.tommap.springaiapp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tommap.springaiapp.TestcontainersConfiguration;
import com.tommap.springaiapp.chat.ChatTestClient;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RevenueIntegrationTest {

    @Autowired
    RevenueService revenue;
    @Autowired
    OrderService orders;
    @Autowired
    OrderSeedService seed;
    @Autowired
    Clock clock;
    @Autowired
    MockMvc mvc;

    @BeforeEach
    void resetShop() {
        seed.reseed();
    }

    @Test
    void theDefaultRangeCountsApprovedOrdersOfTheLastSixtyDays() {
        RevenueSummary summary = revenue.summarize(null, null);

        long approved = orders.listAll(OrderStatus.APPROVED).stream().mapToLong(OrderView::totalAmount).sum();
        LocalDate today = ShopTime.today(clock);
        assertThat(summary.to()).isEqualTo(today);
        assertThat(summary.from()).isEqualTo(today.minusDays(59));
        assertThat(summary.orders()).isEqualTo(3);
        assertThat(summary.revenueVnd()).isEqualTo(approved).isPositive();
        assertThat(summary.bottles()).isEqualTo(3 + 2 + 10);
        assertThat(summary.topProducts()).hasSize(3).isSortedAccordingTo(
                (a, b) -> Long.compare(b.revenueVnd(), a.revenueVnd()));
        assertThat(summary.previous().to()).isEqualTo(summary.from().minusDays(1));
        assertThat(summary.previous().orders()).isZero();
        assertThat(summary.previous().changePercent()).isNull();
    }

    @Test
    void pendingAndRejectedOrdersAreNotRevenueUntilApproved() {
        long before = revenue.summarize(null, null).revenueVnd();

        orders.approve(1042);

        assertThat(revenue.summarize(null, null).revenueVnd()).isEqualTo(before + 190_000);
    }

    @Test
    void anExplicitRangeIsComparedWithTheRangeBeforeIt() {
        LocalDate today = ShopTime.today(clock);

        // Sample order #1017 (approved) is 30 days old, #1009 (approved) 44 days old.
        RevenueSummary summary = revenue.summarize(today.minusDays(31).toString(), today.minusDays(29).toString());

        assertThat(summary.orders()).isEqualTo(1);
        assertThat(summary.previous().from()).isEqualTo(today.minusDays(34));
        assertThat(summary.previous().to()).isEqualTo(today.minusDays(32));
        assertThat(summary.previous().orders()).isZero();
    }

    @Test
    void aBadOrReversedDateIsExplained() {
        assertThatThrownBy(() -> revenue.summarize("last month", null)).hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> revenue.summarize("2026-10-10", "2026-10-01")).hasMessageContaining("after");
    }

    @Test
    void onlyAdministratorsReadRevenueOverRestOrChat() throws Exception {
        ChatTestClient chat = new ChatTestClient(mvc);
        String user = "Bearer " + chat.login("an");
        String admin = "Bearer " + chat.login("admin");

        mvc.perform(get("/api/admin/revenue").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/revenue").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", is(3)));

        var asUser = chat.chat(chat.login("an"), "what is the revenue?");
        assertThat(asUser.toolSteps()).singleElement().satisfies(
                step -> assertThat(step.get("detail").get("name").asString()).isEqualTo("getRevenueSummary"));
        assertThat(asUser.answer()).contains("Only the shop administrator").doesNotContain("revenueVnd");

        var asAdmin = chat.chat(chat.login("admin"), "what is the revenue?");
        assertThat(asAdmin.toolSteps().getFirst().get("detail").get("result").asString()).contains("revenueVnd");
        assertThat(asAdmin.answer()).startsWith("Revenue from").contains("approved order(s)");
    }
}
