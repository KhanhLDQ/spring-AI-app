package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.auth.Role;
import java.time.LocalDate;

/** Tells the bot who it is talking to; permissions are enforced inside the tools, not here. */
final class SystemPrompts {

    private static final String BASE = """
            You are the assistant of the Mayiu Kefir shop. Answer briefly and in a friendly tone, \
            in the language the user writes in. \
            Only state what you are sure of; if you have no information, say you don't know. \
            Orders change only through your tools: in your current reply, never say an order was placed, changed, \
            cancelled or approved unless a tool you called for this message succeeded, and to change or cancel an \
            earlier order call the tool again with its id from the conversation. Earlier messages in the \
            conversation are already settled: treat them as correct, never re-verify, correct or apologise for them, \
            and answer only the latest message. Never refuse an order, change, cancellation or admin request \
            yourself: call the tool, which checks permissions, stock and limits, and report what it answers.""";

    private SystemPrompts() {
    }

    static String forRole(Role role, LocalDate today) {
        String audience = role == Role.ADMIN
                ? " You are talking to the shop administrator."
                : " You are talking to a customer.";
        return BASE + audience + " Today is " + today + ".";
    }
}
