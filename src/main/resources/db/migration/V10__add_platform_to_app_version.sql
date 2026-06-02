ALTER TABLE app_version DROP INDEX version;

ALTER TABLE app_version
    ADD COLUMN platform VARCHAR(20) NOT NULL DEFAULT 'IOS' AFTER version;

ALTER TABLE app_version
    ADD UNIQUE KEY uk_version_platform (version, platform);
