package com.tommap.springaiapp.catalog;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final ProductRepository products;

    CatalogService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<ProductView> list() {
        return products.findAll().stream().map(ProductView::of).toList();
    }

    @Transactional(readOnly = true)
    public ProductView get(String query) {
        return ProductView.of(resolve(query));
    }

    /** Accepts a SKU or a product name, ignoring case and accents; fails unless exactly one product matches. */
    @Transactional(readOnly = true)
    public Product resolve(String query) {
        String wanted = normalize(query);
        List<Product> all = products.findAll();
        List<Product> exact = all.stream()
                .filter(p -> normalize(p.getSku()).equals(wanted) || normalize(p.displayName()).equals(wanted))
                .toList();
        List<Product> matches = !exact.isEmpty() ? exact
                : all.stream().filter(p -> !wanted.isEmpty() && normalize(p.displayName()).contains(wanted)).toList();
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        String choices = all.stream().map(p -> p.getSku() + " (" + p.displayName() + ")")
                .collect(Collectors.joining(", "));
        throw new UnknownProductException((matches.isEmpty() ? "No product matches '" : "'") + query
                + (matches.isEmpty() ? "'" : "' matches several products") + ". Use one of: " + choices);
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}
