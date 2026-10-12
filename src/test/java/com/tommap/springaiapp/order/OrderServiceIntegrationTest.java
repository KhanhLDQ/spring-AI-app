package com.tommap.springaiapp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tommap.springaiapp.TestcontainersConfiguration;
import com.tommap.springaiapp.catalog.UnknownProductException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderServiceIntegrationTest {

    private static final String HONEY_330 = "MAT-ONG-330";

    @Autowired
    OrderService orders;
    @Autowired
    OrderSeedService seed;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetShop() {
        seed.reseed();
    }

    @Test
    void seedLoadsSampleOrdersForBothUsers() {
        assertThat(orders.listMine("an")).extracting(OrderView::id).containsExactly(1042L, 1017L, 1009L, 1003L);
        assertThat(orders.listAll(OrderStatus.PENDING)).extracting(OrderView::id).containsExactly(1042L, 1031L);
        OrderView sample = orders.listMine("an").getFirst();
        assertThat(sample.totalAmount()).isEqualTo(190_000);
        assertThat(sample.items()).singleElement().satisfies(line -> {
            assertThat(line.product()).isEqualTo("Kefir Mật Ong");
            assertThat(line.size()).isEqualTo("330ml");
        });
    }

    @Test
    void createReservesStockAndPricesTheOrder() {
        OrderView order = orders.create("an", "Kefir Mật Ong 330ml", 3);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.totalAmount()).isEqualTo(3 * 38_000);
        assertThat(stock(HONEY_330)).isEqualTo(21);
    }

    @Test
    void productCanBeNamedWithoutAccents() {
        assertThat(orders.create("an", "kefir mat ong 330ml", 1).items()).singleElement()
                .satisfies(line -> assertThat(line.size()).isEqualTo("330ml"));
    }

    @Test
    void ambiguousProductListsTheValidChoices() {
        assertThatThrownBy(() -> orders.create("an", "Kefir Mật Ong", 1))
                .isInstanceOf(UnknownProductException.class)
                .hasMessageContaining("matches several products")
                .hasMessageContaining(HONEY_330);
    }

    @Test
    void fiveHundredBottlesAreRefusedWithAReasonTheModelCanRelay() {
        assertThatThrownBy(() -> orders.create("an", HONEY_330, 500))
                .isInstanceOf(OrderRuleException.class)
                .hasMessageContaining("maximum is 20")
                .hasMessageContaining("24 in stock");
        assertThat(stock(HONEY_330)).isEqualTo(24);
    }

    @Test
    void orderingMoreThanTheStockIsRefused() {
        jdbc.update("UPDATE products SET stock = 3 WHERE sku = ?", HONEY_330);

        assertThatThrownBy(() -> orders.create("an", HONEY_330, 5))
                .isInstanceOf(OrderRuleException.class)
                .hasMessageContaining("Only 3 left");
        assertThat(stock(HONEY_330)).isEqualTo(3);
    }

    @Test
    void updatingQuantityMovesStockByTheDifference() {
        long id = orders.create("an", HONEY_330, 5).id();

        OrderView grown = orders.updateQuantity("an", id, 8);
        assertThat(grown.totalAmount()).isEqualTo(8 * 38_000);
        assertThat(stock(HONEY_330)).isEqualTo(16);

        orders.updateQuantity("an", id, 2);
        assertThat(stock(HONEY_330)).isEqualTo(22);
    }

    @Test
    void updateCannotExceedWhatIsLeftEvenCountingTheOrderItself() {
        jdbc.update("UPDATE products SET stock = 4 WHERE sku = ?", HONEY_330);
        long id = orders.create("an", HONEY_330, 3).id();

        assertThatThrownBy(() -> orders.updateQuantity("an", id, 9))
                .isInstanceOf(OrderRuleException.class).hasMessageContaining("Only 4 left");
        assertThat(orders.updateQuantity("an", id, 4).items().getFirst().quantity()).isEqualTo(4);
        assertThat(stock(HONEY_330)).isZero();
    }

    @Test
    void usersCannotTouchEachOthersOrders() {
        assertThatThrownBy(() -> orders.cancel("an", 1031)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> orders.updateQuantity("an", 1031, 1)).isInstanceOf(OrderNotFoundException.class);
        assertThat(orders.listMine("an")).extracting(OrderView::id).doesNotContain(1031L);
    }

    @Test
    void onlyPendingOrdersCanBeChangedOrCancelled() {
        assertThatThrownBy(() -> orders.cancel("an", 1017))
                .isInstanceOf(OrderRuleException.class).hasMessageContaining("APPROVED");
        assertThatThrownBy(() -> orders.updateQuantity("an", 1003, 2))
                .isInstanceOf(OrderRuleException.class).hasMessageContaining("REJECTED");
    }

    @Test
    void cancelAndRejectReturnStockToTheShelf() {
        long cancelled = orders.create("an", HONEY_330, 4).id();
        long rejected = orders.create("an", HONEY_330, 6).id();
        assertThat(stock(HONEY_330)).isEqualTo(14);

        assertThat(orders.cancel("an", cancelled).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orders.reject(rejected).status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(stock(HONEY_330)).isEqualTo(24);
    }

    @Test
    void approveIsOneWay() {
        assertThat(orders.approve(1042).status()).isEqualTo(OrderStatus.APPROVED);

        assertThatThrownBy(() -> orders.approve(1042)).isInstanceOf(OrderRuleException.class);
        assertThatThrownBy(() -> orders.reject(1042)).isInstanceOf(OrderRuleException.class);
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        int attempts = 2;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Boolean> fifteenBottles = () -> {
            go.await();
            try {
                orders.create("an", HONEY_330, 15);
                return true;
            } catch (OrderRuleException e) {
                return false;
            }
        };
        List<Future<Boolean>> results = List.of(pool.submit(fifteenBottles), pool.submit(fifteenBottles));
        go.countDown();

        long succeeded = 0;
        for (Future<Boolean> result : results) {
            succeeded += result.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(stock(HONEY_330)).isEqualTo(9);
    }

    private int stock(String sku) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE sku = ?", Integer.class, sku);
    }
}
