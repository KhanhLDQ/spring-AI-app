package com.tommap.springaiapp.catalog;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Thin adapter: the model-facing description lives here, the logic in {@link CatalogService}. */
@Component
public class CatalogTools {

    private final CatalogService catalog;

    CatalogTools(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Tool(description = """
            List every Mayiu Kefir product with its SKU, name (flavour and size), price in VND, current stock, \
            shelf life in days and description. Use it for questions about prices or availability, and to find \
            the SKU before ordering.""")
    public List<ProductView> listProducts() {
        return catalog.list();
    }

    @Tool(description = "Get one product by SKU or by name (for example 'Kefir Mật Ong 330ml'): price in VND and stock.")
    public ProductView getProduct(
            @ToolParam(description = "SKU or product name including the size") String product) {
        return catalog.get(product);
    }
}
