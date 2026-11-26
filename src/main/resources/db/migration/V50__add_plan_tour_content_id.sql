-- Tour API place reference (contentid), ADR-0004. A row points at a store (place_id) or a tour place, never both.
ALTER TABLE plan
    ADD COLUMN tour_content_id VARCHAR(20) NULL,
    ADD CONSTRAINT chk_plan_single_place CHECK (place_id IS NULL OR tour_content_id IS NULL);
