package com.tommap.springaiapp.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code signingKey} comes from JWT_SIGNING_KEY; when blank an ephemeral key is generated at startup. */
@ConfigurationProperties("kefir.jwt")
public record JwtProperties(String signingKey, Duration ttl) {

    public JwtProperties {
        if (ttl == null) {
            ttl = Duration.ofHours(24);
        }
    }
}
