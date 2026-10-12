package com.tommap.springaiapp.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** The caller as identified by the verified JWT; never taken from request bodies. */
public record AuthenticatedUser(String username, Role role) {

    public static AuthenticatedUser from(Jwt jwt) {
        return new AuthenticatedUser(jwt.getSubject(), Role.valueOf(jwt.getClaimAsString(JwtService.ROLE_CLAIM)));
    }
}
