package com.tommap.springaiapp.order;

import com.tommap.springaiapp.auth.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/mine")
    List<OrderView> mine(@AuthenticationPrincipal Jwt jwt) {
        return orders.listMine(AuthenticatedUser.from(jwt).username());
    }
}
