-- A withdrawn (DELETED / PURGED) user must carry withdrawn_at, otherwise the purge job never picks it up.
-- Pre-check on prod (2026-10-16): no DELETED row with NULL withdrawn_at, so no backfill is needed.
ALTER TABLE user
    ADD CONSTRAINT chk_user_withdrawn_at CHECK (status = 'NORMAL' OR withdrawn_at IS NOT NULL);
