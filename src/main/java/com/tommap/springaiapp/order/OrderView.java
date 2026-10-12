package com.tommap.springaiapp.order;

import java.time.Instant;
import java.util.List;

/** What the API and the tools expose; the JPA entities never leave this package. */
public record OrderView(long id, String username, OrderStatus status, Instant createdAt, long totalAmount,
                        List<Line> items) {

    public record Line(String product, String size, int quantity, long unitPrice) {
    }

    static OrderView of(Order order) {
        List<Line> lines = order.getItems().stream()
                .map(i -> new Line(i.getProduct().getFlavor(), i.getProduct().getSize(), i.getQuantity(),
                        i.getUnitPrice()))
                .toList();
        return new OrderView(order.getId(), order.getUsername(), order.getStatus(), order.getCreatedAt(),
                order.getTotalAmount(), lines);
    }
}
