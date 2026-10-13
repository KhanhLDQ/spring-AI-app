package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;

@Service
class ChatHistoryService {

    private final ChatMemory chatMemory;

    ChatHistoryService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /** What the model would be given on the next turn: the latest window of user and assistant messages. */
    List<HistoryMessage> history(AuthenticatedUser user, UUID conversationId) {
        return chatMemory.get(ChatService.memoryKey(user, conversationId)).stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> new HistoryMessage(m.getMessageType() == MessageType.USER ? "user" : "assistant", m.getText()))
                .toList();
    }

    record HistoryMessage(String role, String text) {
    }
}
