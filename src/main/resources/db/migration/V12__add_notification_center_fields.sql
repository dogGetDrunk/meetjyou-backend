ALTER TABLE user_party
    ADD COLUMN application_note VARCHAR(500) NULL,
    ADD COLUMN host_read        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN applicant_read   BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE user
    ADD COLUMN last_notices_viewed_at TIMESTAMP NULL;
