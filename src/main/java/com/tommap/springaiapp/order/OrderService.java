package com.tommap.springaiapp.order;

import com.tommap.springaiapp.catalog.CatalogService;
import com.tommap.springaiapp.catalog.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    static final int MAX_PER_ORDER = 20;

    private final OrderRepository orders;
    private final CatalogService catalog;
    private final Clock clock;
    private final EntityManager entityManager;

    OrderService(OrderRepository orders, CatalogService catalog, Clock clock, EntityManager entityManager) {
        this.orders = orders;
        this.catalog = catalog;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    public OrderView create(String username, String productQuery, int quantity) {
        Product product = lockedProduct(catalog.resolve(productQuery).getId());
        checkQuantity(product, quantity, 0);
        product.adjustStock(-quantity);
        Order order = new Order(username, Instant.now(clock));
        order.addItem(new OrderItem(order, product, quantity));
        return OrderView.of(orders.save(order));
    }

    @Transactional
    public OrderView updateQuantity(String username, long orderId, int newQuantity) {
        Order order = pendingOwnedBy(username, orderId, "changed");
        OrderItem item = order.getItems().getFirst();
        Product product = lockedProduct(item.getProduct().getId());
        checkQuantity(product, newQuantity, item.getQuantity());
        product.adjustStock(item.getQuantity() - newQuantity);
        item.setQuantity(newQuantity);
        order.recalculateTotal();
        return OrderView.of(order);
    }

    @Transactional
    public OrderView cancel(String username, long orderId) {
        return release(pendingOwnedBy(username, orderId, "cancelled"), OrderStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public List<OrderView> listMine(String username) {
        return orders.findByUsernameOrderByCreatedAtDesc(username).stream().map(OrderView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderView> listAll(OrderStatus status) {
        List<Order> found = status == null ? orders.findAllByOrderByCreatedAtDesc()
                : orders.findByStatusOrderByCreatedAtDesc(status);
        return found.stream().map(OrderView::of).toList();
    }

    @Transactional
    public OrderView approve(long orderId) {
        Order order = pending(orderId, "approved");
        order.transitionTo(OrderStatus.APPROVED);
        return OrderView.of(order);
    }

    @Transactional
    public OrderView reject(long orderId) {
        return release(pending(orderId, "rejected"), OrderStatus.REJECTED);
    }

    private Order pendingOwnedBy(String username, long orderId, String action) {
        Order order = orders.findWithItemsById(orderId)
                .filter(o -> o.getUsername().equals(username))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return requirePending(order, action);
    }

    private Order pending(long orderId, String action) {
        return requirePending(orders.findWithItemsById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId)),
                action);
    }

    private static Order requirePending(Order order, String action) {
        if (!order.isPending()) {
            throw new OrderRuleException("Order #" + order.getId() + " is " + order.getStatus()
                    + " and can no longer be " + action + ".");
        }
        return order;
    }

    /** Ends the order and returns its stock to the shelf. */
    private OrderView release(Order order, OrderStatus finalStatus) {
        for (OrderItem item : order.getItems()) {
            lockedProduct(item.getProduct().getId()).adjustStock(item.getQuantity());
        }
        order.transitionTo(finalStatus);
        return OrderView.of(order);
    }

    /**
     * Locks the row and re-reads it: the product may already be loaded in this transaction (by name lookup or
     * as part of an order), and a plain locking query would hand back that stale copy and its old stock.
     */
    private Product lockedProduct(Long id) {
        Product product = entityManager.find(Product.class, id);
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        return product;
    }

    /** {@code alreadyReserved} is what this order already holds, so editing does not count it twice. */
    private static void checkQuantity(Product product, int quantity, int alreadyReserved) {
        if (quantity < 1) {
            throw new OrderRuleException("The quantity must be at least 1.");
        }
        int available = product.getStock() + alreadyReserved;
        if (quantity > MAX_PER_ORDER) {
            throw new OrderRuleException("The maximum is " + MAX_PER_ORDER + " per order (only " + available
                    + " in stock for " + product.displayName() + ").");
        }
        if (quantity > available) {
            throw new OrderRuleException("Only " + available + " left in stock for " + product.displayName() + ".");
        }
    }
}
