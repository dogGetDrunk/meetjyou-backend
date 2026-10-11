ALTER TABLE notification_outbox
    ADD COLUMN delivered_token_ids JSON NULL;
