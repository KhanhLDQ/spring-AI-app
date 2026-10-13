package com.tommap.springaiapp.auth;

import java.net.URI;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Who may reach /mcp: only a client on this machine, and never a web page. A browser always names its page in the
 * Origin header, so a page on another site (or a rebound DNS name) is refused even though its request arrives
 * from the user's own machine; command-line MCP clients send no Origin at all.
 */
final class McpAccess implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
                                           RequestAuthorizationContext context) {
        var request = context.getRequest();
        return new AuthorizationDecision(isLoopback(request.getRemoteAddr()) && isLocalOrigin(request.getHeader("Origin")));
    }

    static boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address);
    }

    static boolean isLocalOrigin(String origin) {
        if (origin == null) {
            return true;
        }
        try {
            String host = URI.create(origin).getHost();
            return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
