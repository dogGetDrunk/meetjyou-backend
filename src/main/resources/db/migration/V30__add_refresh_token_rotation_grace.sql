ALTER TABLE refresh_token
    ADD COLUMN revoked_at      TIMESTAMP NULL,
    ADD COLUMN replaced_by_jti CHAR(36)  NULL;
