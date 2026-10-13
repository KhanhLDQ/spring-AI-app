package com.tommap.springaiapp.order;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** The shop's calendar: dates a customer or the administrator speaks of are Vietnamese dates, not UTC ones. */
public final class ShopTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ShopTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
