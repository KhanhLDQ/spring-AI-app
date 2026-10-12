package com.tommap.springaiapp.catalog;

public record ProductView(String sku, String name, int priceVnd, int stock, int shelfLifeDays, String description,
                          String image) {

    static ProductView of(Product p) {
        return new ProductView(p.getSku(), p.displayName(), p.getPriceVnd(), p.getStock(), p.getShelfLifeDays(),
                p.getDescription(), p.getImage());
    }
}
