CREATE TABLE IF NOT EXISTS refresh_token
(
    id         BIGINT    AUTO_INCREMENT PRIMARY KEY,
    jti        CHAR(36)  NOT NULL UNIQUE,
    user_id    INT       NOT NULL,
    expires_at DATETIME  NOT NULL,
    revoked    BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
);
