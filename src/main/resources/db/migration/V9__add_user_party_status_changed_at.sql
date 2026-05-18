ALTER TABLE user_party
    ADD COLUMN status_changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE user_party
SET status_changed_at = joined_at;
