-- A social identity (provider, external_id) moves out of the user row so one user can hold
-- several of them. Expand step: user.auth_provider / user.external_id stay and are still written
-- (an image rollback keeps working); lookups read this table from now on.
CREATE TABLE IF NOT EXISTS user_social_account
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    provider    VARCHAR(10)  NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_social_account_provider_external UNIQUE (provider, external_id),
    CONSTRAINT uk_social_account_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_social_account_user FOREIGN KEY (user_id) REFERENCES user (id)
);

INSERT INTO user_social_account (user_id, provider, external_id, email, created_at)
SELECT id, auth_provider, external_id, email, created_at
FROM user;
