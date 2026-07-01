ALTER TABLE user
    ADD COLUMN marketing_sns_consented BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN marketing_email_consented BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user
SET marketing_sns_consented   = marketing_consented,
    marketing_email_consented = marketing_consented;
