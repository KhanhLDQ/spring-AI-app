package com.tommap.springaiapp.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/** {@code off} names the pipeline parts to skip for this message ({@link AdvisorSwitch}); absent means all on. */
record ChatRequest(@NotNull UUID conversationId, @NotBlank @Size(max = 2000) String message,
                   Set<@Pattern(regexp = "memory|cache|rag|tools") String> off) {

    Set<String> offOrEmpty() {
        return off == null ? Set.of() : off;
    }
}
