-- user_terms is an append-only consent history: every change inserts a row and the latest id is the
-- current state. The V18 unique key allowed only one AGREED and one WITHDRAWN row per terms version,
-- so agree -> withdraw -> agree failed on the second AGREED (#224).
-- The new index is added in the same statement because the unique key is also the index backing the
-- user_id foreign key, and MySQL refuses to drop it while no other index covers that column.
ALTER TABLE user_terms
    ADD INDEX idx_user_terms_user_id_terms_id (user_id, terms_id),
    DROP INDEX uk_user_terms_user_id_terms_id_action;
