-- Layout of Spring AI's JDBC chat memory, except conversation_id: ours is "<username>:<uuid>" (up to 86 chars),
-- and the stock VARCHAR(36) would reject it.
CREATE TABLE spring_ai_chat_memory (
    conversation_id VARCHAR(100) NOT NULL,
    content         TEXT         NOT NULL,
    type            VARCHAR(10)  NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     TIMESTAMP    NOT NULL,
    sequence_id     BIGINT       NOT NULL
);

CREATE INDEX spring_ai_chat_memory_conversation_id_timestamp_idx ON spring_ai_chat_memory (conversation_id, "timestamp");
CREATE INDEX spring_ai_chat_memory_conversation_id_sequence_id_idx ON spring_ai_chat_memory (conversation_id, sequence_id);
