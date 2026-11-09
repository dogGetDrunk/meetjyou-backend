-- Representative travel destination of posts, parties and plans: region (required) + place (optional).
-- Rows written before this column existed get '00' (미지정), which search and stats ignore.
ALTER TABLE post
    ADD COLUMN region_code VARCHAR(5) NOT NULL DEFAULT '00',
    ADD COLUMN place_id    BIGINT     NULL,
    ADD CONSTRAINT fk_post_region FOREIGN KEY (region_code) REFERENCES region (code),
    ADD CONSTRAINT fk_post_place FOREIGN KEY (place_id) REFERENCES place (id);

ALTER TABLE party
    ADD COLUMN region_code VARCHAR(5) NOT NULL DEFAULT '00',
    ADD COLUMN place_id    BIGINT     NULL,
    ADD CONSTRAINT fk_party_region FOREIGN KEY (region_code) REFERENCES region (code),
    ADD CONSTRAINT fk_party_place FOREIGN KEY (place_id) REFERENCES place (id);

ALTER TABLE plan
    ADD COLUMN region_code VARCHAR(5) NOT NULL DEFAULT '00',
    ADD COLUMN place_id    BIGINT     NULL,
    ADD CONSTRAINT fk_plan_region FOREIGN KEY (region_code) REFERENCES region (code),
    ADD CONSTRAINT fk_plan_place FOREIGN KEY (place_id) REFERENCES place (id);
