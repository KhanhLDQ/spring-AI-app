package com.tommap.springaiapp.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Role check lives in SecurityConfiguration (/api/admin/** requires ADMIN). */
@RestController
@RequestMapping("/api/admin/revenue")
class AdminRevenueController {

    private final RevenueService revenue;

    AdminRevenueController(RevenueService revenue) {
        this.revenue = revenue;
    }

    @GetMapping
    RevenueSummary summary(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return revenue.summarize(from, to);
    }
}
