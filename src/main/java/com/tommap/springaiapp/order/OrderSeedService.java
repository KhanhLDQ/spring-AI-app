package com.tommap.springaiapp.order;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resets the shop to the demo data: full shelves and six sample orders. */
@Service
public class OrderSeedService {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private record Sample(long id, String username, String sku, int quantity, OrderStatus status, Instant at) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    OrderSeedService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void reseed() {
        jdbc.update("DELETE FROM orders");
        jdbc.update("UPDATE products SET stock = initial_stock");
        for (Sample s : samples()) {
            jdbc.update("INSERT INTO orders (id, username, status, created_at, total_amount) VALUES (?, ?, ?, ?, 0)",
                    s.id(), s.username(), s.status().name(), Timestamp.from(s.at()));
            jdbc.update("""
                    INSERT INTO order_items (order_id, product_id, quantity, unit_price)
                    SELECT ?, id, ?, price_vnd FROM products WHERE sku = ?""", s.id(), s.quantity(), s.sku());
            jdbc.update("""
                    UPDATE orders SET total_amount =
                        (SELECT SUM(quantity * unit_price) FROM order_items WHERE order_id = ?)
                    WHERE id = ?""", s.id(), s.id());
        }
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class) == 0;
    }

    private List<Sample> samples() {
        LocalDate today = LocalDate.now(clock.withZone(SHOP_ZONE));
        return List.of(
                new Sample(1042, "an", "MAT-ONG-330", 5, OrderStatus.PENDING, at(today, 14, 32)),
                new Sample(1031, "admin", "NGUYEN-BAN-1000", 4, OrderStatus.PENDING, at(today, 9, 10)),
                new Sample(1017, "an", "HY-LAP-250", 3, OrderStatus.APPROVED, at(today.minusDays(30), 16, 5)),
                new Sample(1009, "an", "NGUYEN-BAN-330", 2, OrderStatus.APPROVED, at(today.minusDays(44), 10, 41)),
                new Sample(1006, "admin", "ME-DEN-500", 10, OrderStatus.APPROVED, at(today.minusDays(52), 8, 20)),
                new Sample(1003, "an", "MAT-ONG-500", 6, OrderStatus.REJECTED, at(today.minusDays(62), 17, 48)));
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(LocalTime.of(hour, minute)).atZone(SHOP_ZONE).toInstant();
    }
}
