-- notification_outbox: @Scheduled(fixedDelay=2000) 마다 풀스캔
CREATE INDEX idx_notification_outbox_status_available
    ON notification_outbox (status, available_at);

-- chat_message: 채팅 페이지네이션 조회마다 풀스캔
CREATE INDEX idx_chat_message_room_cursor
    ON chat_message (room_id, created_at DESC, id DESC);

-- user_party: 파티/채팅 조회마다 풀스캔
CREATE INDEX idx_user_party_party_user
    ON user_party (party_id, user_id);
