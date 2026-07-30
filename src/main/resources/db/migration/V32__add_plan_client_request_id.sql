ALTER TABLE plan
    ADD COLUMN client_request_id CHAR(36) NULL,
    ADD CONSTRAINT uk_plan_owner_client_request_id
        UNIQUE (owner_id, client_request_id);
