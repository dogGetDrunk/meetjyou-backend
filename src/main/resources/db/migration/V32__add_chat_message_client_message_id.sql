ALTER TABLE chat_message
    ADD COLUMN client_message_id CHAR(36) NULL,
    ADD CONSTRAINT uk_chat_message_room_sender_client_id
        UNIQUE (room_id, sender_id, client_message_id);
