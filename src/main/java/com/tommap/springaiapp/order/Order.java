package com.tommap.springaiapp.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_id")
    @SequenceGenerator(name = "order_id", sequenceName = "order_id_seq", allocationSize = 1)
    private Long id;

    private String username;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Instant createdAt;
    private long totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    Order(String username, Instant createdAt) {
        this.username = username;
        this.createdAt = createdAt;
        this.status = OrderStatus.PENDING;
    }

    Long getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    OrderStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    long getTotalAmount() {
        return totalAmount;
    }

    List<OrderItem> getItems() {
        return items;
    }

    void addItem(OrderItem item) {
        items.add(item);
        recalculateTotal();
    }

    void recalculateTotal() {
        totalAmount = items.stream().mapToLong(OrderItem::lineTotal).sum();
    }

    boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    void transitionTo(OrderStatus next) {
        this.status = next;
    }
}
