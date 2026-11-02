-- These unique indexes were created unnamed in V1, so MySQL named them after their first column.
-- The code maps a violation to a 409 by index name, and a bare `token` / `user_id` could later
-- collide with another table's auto-named index. RENAME INDEX only changes metadata.
ALTER TABLE push_token
    RENAME INDEX token TO uk_push_token_token;

ALTER TABLE chat_participant
    RENAME INDEX user_id TO uk_chat_participant_user_room;
