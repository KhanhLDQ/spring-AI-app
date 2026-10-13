package com.tommap.springaiapp.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Role check lives in SecurityConfiguration (/api/admin/** requires ADMIN); the profile keeps it out of production. */
@RestController
@Profile("dev | demo | mock")
@RequestMapping("/api/admin/demo")
class DemoController {

    private final DemoService demo;

    DemoController(DemoService demo) {
        this.demo = demo;
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset() {
        demo.reset();
    }

    @PostMapping("/reseed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reseed() {
        demo.reseed();
    }

    @PostMapping("/clear-cache")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearCache() {
        demo.clearCache();
    }

    @PostMapping("/clear-vectors")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearVectors() {
        demo.clearVectors();
    }

    @PostMapping("/clear-memory")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearMemory() {
        demo.clearMemory();
    }

    @PutMapping("/threshold")
    DemoService.Thresholds threshold(@RequestBody @Valid ThresholdRequest request) {
        return demo.setThresholds(request.cache(), request.rag(), request.route());
    }

    record ThresholdRequest(@DecimalMin("0.0") @DecimalMax("1.0") Double cache,
                            @DecimalMin("0.0") @DecimalMax("1.0") Double rag,
                            @DecimalMin("0.0") @DecimalMax("1.0") Double route) {
    }
}
