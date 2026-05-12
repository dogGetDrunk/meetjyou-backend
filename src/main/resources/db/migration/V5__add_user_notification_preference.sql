CREATE TABLE IF NOT EXISTS user_notification_preference
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           INT         NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_unp_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
    CONSTRAINT uk_unp_user_type UNIQUE (user_id, notification_type)
);
