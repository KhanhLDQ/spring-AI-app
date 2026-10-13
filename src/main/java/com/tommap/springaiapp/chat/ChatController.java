package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService history;

    ChatController(ChatService chatService, ChatHistoryService history) {
        this.chatService = chatService;
        this.history = history;
    }

    @PostMapping("/new")
    Map<String, UUID> newConversation() {
        return Map.of("conversationId", UUID.randomUUID());
    }

    @GetMapping("/history")
    List<ChatHistoryService.HistoryMessage> history(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID conversationId) {
        return history.history(AuthenticatedUser.from(jwt), conversationId);
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Object>> stream(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest request) {
        return chatService.stream(AuthenticatedUser.from(jwt), request.conversationId(), request.message())
                .map(event -> ServerSentEvent.<Object>builder(event.payload()).event(event.name()).build());
    }
}
