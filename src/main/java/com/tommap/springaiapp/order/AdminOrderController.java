package com.tommap.springaiapp.order;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Role check lives in SecurityConfiguration (/api/admin/** requires ADMIN). */
@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController {

    private final OrderService orders;

    AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    List<OrderView> list(@RequestParam(required = false) OrderStatus status) {
        return orders.listAll(status);
    }

    @PostMapping("/{id}/approve")
    OrderView approve(@PathVariable long id) {
        return orders.approve(id);
    }

    @PostMapping("/{id}/reject")
    OrderView reject(@PathVariable long id) {
        return orders.reject(id);
    }
}
