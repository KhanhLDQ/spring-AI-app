package com.tommap.springaiapp.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private String flavor;
    private String size;
    private int priceVnd;
    private int stock;
    private int shelfLifeDays;
    private String description;
    private String image;

    protected Product() {
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getFlavor() {
        return flavor;
    }

    public String getSize() {
        return size;
    }

    public String displayName() {
        return flavor + " " + size;
    }

    public int getPriceVnd() {
        return priceVnd;
    }

    public int getStock() {
        return stock;
    }

    public int getShelfLifeDays() {
        return shelfLifeDays;
    }

    public String getDescription() {
        return description;
    }

    public String getImage() {
        return image;
    }

    /** Callers must hold a pessimistic lock on the row, otherwise concurrent orders can oversell. */
    public void adjustStock(int delta) {
        if (stock + delta < 0) {
            throw new IllegalStateException("Stock cannot go negative for " + sku);
        }
        stock += delta;
    }
}
