package com.tommap.springaiapp.order;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevenueService {

    static final int DEFAULT_DAYS = 60;
    private static final int TOP_PRODUCTS = 3;

    private final OrderRepository orders;
    private final Clock clock;

    RevenueService(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    /** Either end may be empty: the range then ends today and/or reaches back DEFAULT_DAYS. */
    @Transactional(readOnly = true)
    public RevenueSummary summarize(String fromDate, String toDate) {
        LocalDate today = ShopTime.today(clock);
        LocalDate to = parse(toDate, "toDate", today);
        LocalDate from = parse(fromDate, "fromDate", to.minusDays(DEFAULT_DAYS - 1L));
        if (from.isAfter(to)) {
            throw new OrderRuleException("fromDate " + from + " is after toDate " + to + ".");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);

        List<Order> current = approved(from, to);
        List<Order> previous = approved(previousFrom, previousTo);
        long revenue = total(current);
        long previousRevenue = total(previous);
        Double change = previousRevenue == 0 ? null
                : Math.round((revenue - previousRevenue) * 1000.0 / previousRevenue) / 10.0;
        return new RevenueSummary(from, to, revenue, current.size(), bottles(current), topProducts(current),
                new RevenueSummary.Previous(previousFrom, previousTo, previousRevenue, previous.size(), change));
    }

    private List<Order> approved(LocalDate from, LocalDate to) {
        return orders.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(OrderStatus.APPROVED,
                from.atStartOfDay(ShopTime.ZONE).toInstant(), to.plusDays(1).atStartOfDay(ShopTime.ZONE).toInstant());
    }

    private static LocalDate parse(String value, String name, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new OrderRuleException(name + " '" + value + "' is not a date. Use the format YYYY-MM-DD.");
        }
    }

    private static long total(List<Order> orders) {
        return orders.stream().mapToLong(Order::getTotalAmount).sum();
    }

    private static int bottles(List<Order> orders) {
        return orders.stream().flatMap(o -> o.getItems().stream()).mapToInt(OrderItem::getQuantity).sum();
    }

    private static List<RevenueSummary.TopProduct> topProducts(List<Order> orders) {
        Map<String, long[]> byProduct = new HashMap<>();
        Map<String, String[]> names = new HashMap<>();
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                String sku = item.getProduct().getSku();
                names.putIfAbsent(sku, new String[] {item.getProduct().getFlavor(), item.getProduct().getSize()});
                long[] sums = byProduct.computeIfAbsent(sku, k -> new long[2]);
                sums[0] += item.getQuantity();
                sums[1] += item.lineTotal();
            }
        }
        return byProduct.entrySet().stream()
                .map(e -> new RevenueSummary.TopProduct(names.get(e.getKey())[0], names.get(e.getKey())[1],
                        (int) e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(RevenueSummary.TopProduct::revenueVnd).reversed())
                .limit(TOP_PRODUCTS)
                .toList();
    }
}
