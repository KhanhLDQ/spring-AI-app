package com.tommap.springaiapp.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
class ProductController {

    private final CatalogService catalog;

    ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<ProductView> list() {
        return catalog.list();
    }
}
