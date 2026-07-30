ALTER TABLE post
    ADD COLUMN client_request_id CHAR(36) NULL,
    ADD CONSTRAINT uk_post_author_client_request_id
        UNIQUE (author_id, client_request_id);
