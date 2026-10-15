package com.tommap.springaiapp.chat;

import java.util.Collection;
import java.util.Map;

/**
 * Parts of the chat pipeline a message can turn off, so a demo can add them one at a time. A switched-off advisor
 * still records an Inspector step saying so, then passes the request straight through.
 */
public final class AdvisorSwitch {

    /** Advisor-context parameter holding the names that are off for this message. */
    public static final String OFF_PARAM = "kefir.off";

    public static final String MEMORY = "memory";
    public static final String CACHE = "cache";
    public static final String RAG = "rag";
    public static final String TOOLS = "tools";

    private AdvisorSwitch() {
    }

    public static boolean isOff(Map<String, Object> context, String name) {
        return context.get(OFF_PARAM) instanceof Collection<?> off && off.contains(name);
    }
}
