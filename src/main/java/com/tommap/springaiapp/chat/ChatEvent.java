package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.trace.TraceStep;
import java.math.BigDecimal;
import java.util.Map;

/** Server-sent events of POST /api/chat/stream (see docs/contract.md). */
sealed interface ChatEvent {

    String name();

    Object payload();

    record Step(TraceStep step) implements ChatEvent {
        public String name() {
            return "step";
        }

        public Object payload() {
            return step;
        }
    }

    record Token(String text) implements ChatEvent {
        public String name() {
            return "token";
        }

        public Object payload() {
            return new TokenPayload(text);
        }
    }

    /** {@code stream}: what arrived on the model stream ({@link StreamTrace}); empty when the model was not called. */
    record Done(long ms, long tokensIn, long tokensOut, BigDecimal cost, String cache, Map<String, Object> stream)
            implements ChatEvent {
        public String name() {
            return "done";
        }

        public Object payload() {
            return this;
        }
    }

    record Error(String message) implements ChatEvent {
        public String name() {
            return "error";
        }

        public Object payload() {
            return this;
        }
    }

    record TokenPayload(String text) {
    }
}
