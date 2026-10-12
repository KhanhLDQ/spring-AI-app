package com.tommap.springaiapp.order;

import com.tommap.springaiapp.catalog.Product;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;
    private long unitPrice;

    protected OrderItem() {
    }

    OrderItem(Order order, Product product, int quantity) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getPriceVnd();
    }

    Product getProduct() {
        return product;
    }

    int getQuantity() {
        return quantity;
    }

    long getUnitPrice() {
        return unitPrice;
    }

    void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    long lineTotal() {
        return unitPrice * quantity;
    }
}
