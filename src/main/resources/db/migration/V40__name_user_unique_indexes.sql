-- The nickname / external_id unique indexes were created unnamed in V1, so MySQL named them after
-- their columns. The code maps a violation to a 409 by index name, so give them explicit names.
-- RENAME INDEX only changes metadata: no table rebuild.
ALTER TABLE user
    RENAME INDEX nickname TO uk_user_nickname,
    RENAME INDEX external_id TO uk_user_external_id;
