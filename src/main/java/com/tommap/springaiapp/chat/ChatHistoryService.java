package com.tommap.springaiapp.chat;

import com.tommap.springaiapp.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {

    private final ChatMemory chatMemory;
    private final JdbcTemplate jdbc;

    ChatHistoryService(ChatMemory chatMemory, JdbcTemplate jdbc) {
        this.chatMemory = chatMemory;
        this.jdbc = jdbc;
    }

    /** Every conversation of every user; ChatMemory can only clear one conversation at a time. */
    public void clearAll() {
        jdbc.execute("TRUNCATE TABLE spring_ai_chat_memory");
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
