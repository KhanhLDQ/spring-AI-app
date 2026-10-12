package com.tommap.springaiapp.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    @GetMapping("/me")
    AuthenticatedUser me(@AuthenticationPrincipal Jwt jwt) {
        return AuthenticatedUser.from(jwt);
    }
}
