-- Broadcasts check "already sent" with a dedup_key prefix lookup (LIKE 'notice:<uuid>:%'),
-- which would otherwise scan the whole outbox. Plain (non-unique) index: it only serves that lookup.
CREATE INDEX idx_notification_outbox_dedup_key ON notification_outbox (dedup_key);
