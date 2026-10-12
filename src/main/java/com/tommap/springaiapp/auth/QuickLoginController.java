package com.tommap.springaiapp.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Demo-only shortcut: the endpoint does not exist unless the {@code demo} or {@code mock} profile is active. */
@RestController
@Profile("demo | mock")
@RequestMapping("/api/auth")
class QuickLoginController {

    private final UserRepository users;
    private final JwtService jwtService;

    QuickLoginController(UserRepository users, JwtService jwtService) {
        this.users = users;
        this.jwtService = jwtService;
    }

    @PostMapping("/quick-login")
    LoginResponse quickLogin(@Valid @RequestBody LoginRequest request) {
        User user = users.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown demo user"));
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.value(), token.expiresInSeconds(),
                new AuthenticatedUser(user.getUsername(), user.getRole()));
    }

    record LoginRequest(@NotBlank String username) {
    }

    record LoginResponse(String token, long expiresIn, AuthenticatedUser user) {
    }
}
