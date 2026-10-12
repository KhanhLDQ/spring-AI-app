package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.auth.Role;

/** Tells the bot who it is talking to; permissions are enforced inside the tools, not here. */
final class SystemPrompts {

    private static final String BASE = """
            You are the assistant of the Mayiu Kefir shop. Answer briefly and in a friendly tone, \
            in the language the user writes in. \
            Only state what you are sure of; if you have no information, say you don't know.""";

    private SystemPrompts() {
    }

    static String forRole(Role role) {
        return role == Role.ADMIN
                ? BASE + " You are talking to the shop administrator."
                : BASE + " You are talking to a customer.";
    }
}
