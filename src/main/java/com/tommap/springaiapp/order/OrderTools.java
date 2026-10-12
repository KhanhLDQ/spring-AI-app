package com.tommap.springaiapp.order;

import com.tommap.springaiapp.auth.ToolCaller;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Thin adapters over {@link OrderService}. The caller always comes from the {@link ToolContext}; a refused request
 * throws, and the exception message is what the model reads back and explains to the user.
 */
@Component
public class OrderTools {

    private final OrderService orders;

    OrderTools(OrderService orders) {
        this.orders = orders;
    }

    @Tool(description = """
            Place an order for the current user right away, without asking for confirmation. The order starts as \
            PENDING until an administrator approves it. At most 20 bottles per order and never more than the \
            stock. Prices are in VND.""")
    public OrderView createOrder(
            @ToolParam(description = "SKU or product name including the size, e.g. MAT-ONG-330") String product,
            @ToolParam(description = "Number of bottles, 1 to 20") int quantity,
            ToolContext context) {
        return orders.create(ToolCaller.from(context).username(), product, quantity);
    }

    @Tool(description = "Change the quantity of one of the current user's own orders. Only PENDING orders can change.")
    public OrderView updateOrderQuantity(
            @ToolParam(description = "Order id, e.g. 1042") long orderId,
            @ToolParam(description = "New number of bottles, 1 to 20") int newQuantity,
            ToolContext context) {
        return orders.updateQuantity(ToolCaller.from(context).username(), orderId, newQuantity);
    }

    @Tool(description = "Cancel one of the current user's own orders and return its bottles to stock. "
            + "Only PENDING orders can be cancelled.")
    public OrderView cancelOrder(
            @ToolParam(description = "Order id, e.g. 1042") long orderId,
            ToolContext context) {
        return orders.cancel(ToolCaller.from(context).username(), orderId);
    }

    @Tool(description = "List the current user's own orders, newest first, with status and total in VND.")
    public List<OrderView> listMyOrders(ToolContext context) {
        return orders.listMine(ToolCaller.from(context).username());
    }

    @Tool(description = "ADMIN ONLY. List orders of all customers, newest first, optionally only one status.")
    public List<OrderView> listAllOrders(
            @ToolParam(description = "PENDING, APPROVED, REJECTED or CANCELLED; leave empty for all", required = false)
            String status,
            ToolContext context) {
        ToolCaller.from(context).requireAdmin("list the orders of all customers");
        return orders.listAll(parseStatus(status));
    }

    @Tool(description = "ADMIN ONLY. Approve a PENDING order of any customer.")
    public OrderView approveOrder(
            @ToolParam(description = "Order id, e.g. 1042") long orderId,
            ToolContext context) {
        ToolCaller.from(context).requireAdmin("approve orders");
        return orders.approve(orderId);
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new OrderRuleException("Unknown status '" + status + "'. Use PENDING, APPROVED, REJECTED or CANCELLED.");
        }
    }
}
