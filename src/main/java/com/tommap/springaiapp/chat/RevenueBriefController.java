package com.tommap.springaiapp.chat;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role check lives in SecurityConfiguration (/api/admin/** requires ADMIN). */
@RestController
@RequestMapping("/api/admin/revenue/brief")
class RevenueBriefController {

    private final RevenueBriefService briefs;

    RevenueBriefController(RevenueBriefService briefs) {
        this.briefs = briefs;
    }

    @PostMapping
    RevenueBriefService.Result brief(@RequestBody(required = false) Range range) {
        Range requested = range == null ? new Range(null, null) : range;
        return briefs.brief(requested.from(), requested.to());
    }

    /** Both ends optional, in the same YYYY-MM-DD form as the revenue endpoint. */
    record Range(String from, String to) {
    }
}
