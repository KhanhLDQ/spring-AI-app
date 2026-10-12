package com.tommap.springaiapp.auth;

import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Who is calling a tool. Identity travels in the {@link ToolContext}, never in tool arguments, so the model cannot
 * choose whose orders it touches, and admin rights are checked here rather than trusted to the system prompt.
 */
public record ToolCaller(String username, Role role) {

    private static final String USERNAME_KEY = "userId";
    private static final String ROLE_KEY = "role";
    private static final String CONVERSATION_KEY = "conversationId";

    public static Map<String, Object> contextFor(AuthenticatedUser user, String conversationId) {
        return Map.of(USERNAME_KEY, user.username(), ROLE_KEY, user.role().name(), CONVERSATION_KEY, conversationId);
    }

    public static ToolCaller from(ToolContext context) {
        Map<String, Object> values = context.getContext();
        return new ToolCaller((String) values.get(USERNAME_KEY), Role.valueOf((String) values.get(ROLE_KEY)));
    }

    public void requireAdmin(String action) {
        if (role != Role.ADMIN) {
            throw new AdminOnlyException(action);
        }
    }
}
