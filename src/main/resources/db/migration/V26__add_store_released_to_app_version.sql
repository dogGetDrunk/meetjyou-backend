ALTER TABLE app_version ADD COLUMN store_released TINYINT(1) NOT NULL DEFAULT 0;

-- Existing rows predate this flag and are already live in the store.
UPDATE app_version SET store_released = 1;
