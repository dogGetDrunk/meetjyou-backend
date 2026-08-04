CREATE TABLE IF NOT EXISTS idempotency_key
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope           VARCHAR(30)  NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    resource_uuid   CHAR(36)     NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id         INT          NOT NULL,
    UNIQUE KEY uk_idempotency_scope_user_key (scope, user_id, idempotency_key)
);

ALTER TABLE idempotency_key
    ADD FOREIGN KEY (user_id) REFERENCES user (id);
