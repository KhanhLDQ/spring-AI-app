package com.tommap.springaiapp.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record ChatRequest(@NotNull UUID conversationId, @NotBlank @Size(max = 2000) String message) {
}
