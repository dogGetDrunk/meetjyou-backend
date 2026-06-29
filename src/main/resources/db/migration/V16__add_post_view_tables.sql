SET @drop_views = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE post DROP COLUMN views',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'post'
      AND COLUMN_NAME = 'views'
);
PREPARE stmt FROM @drop_views;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE post_view_counts
(
    post_id INT NOT NULL PRIMARY KEY,
    views   INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_post_view_counts_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE
);

CREATE TABLE post_view_logs
(
    user_id   INT      NOT NULL,
    post_id   INT      NOT NULL,
    viewed_at DATETIME NOT NULL,
    PRIMARY KEY (user_id, post_id),
    INDEX idx_post_view_logs_viewed_at (viewed_at),
    CONSTRAINT fk_post_view_logs_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_view_logs_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE
);
