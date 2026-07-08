ALTER TABLE user_terms
    DROP INDEX uk_user_terms_user_id_terms_id,
    ADD COLUMN action VARCHAR(20) NOT NULL DEFAULT 'AGREED' AFTER terms_id,
    CHANGE COLUMN agreed_at acted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD UNIQUE KEY uk_user_terms_user_id_terms_id_action (user_id, terms_id, action);
