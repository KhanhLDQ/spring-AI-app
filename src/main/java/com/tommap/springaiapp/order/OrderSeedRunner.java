package com.tommap.springaiapp.order;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Loads the sample orders on first start; later starts keep whatever the demo has produced. */
@Component
class OrderSeedRunner implements ApplicationRunner {

    private final OrderSeedService seed;

    OrderSeedRunner(OrderSeedService seed) {
        this.seed = seed;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seed.isEmpty()) {
            seed.reseed();
        }
    }
}
