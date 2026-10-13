package com.tommap.springaiapp.order;

import java.time.LocalDate;
import java.util.List;

/**
 * Revenue of APPROVED orders in a date range (both ends included, shop time), next to the range of the same length
 * just before it. Typed on purpose: it is what the tool returns and what the admin card renders.
 */
public record RevenueSummary(LocalDate from, LocalDate to, long revenueVnd, int orders, int bottles,
                             List<TopProduct> topProducts, Previous previous) {

    public record TopProduct(String product, String size, int bottles, long revenueVnd) {
    }

    /** changePercent is null when the previous range had no revenue to compare with. */
    public record Previous(LocalDate from, LocalDate to, long revenueVnd, int orders, Double changePercent) {
    }
}
