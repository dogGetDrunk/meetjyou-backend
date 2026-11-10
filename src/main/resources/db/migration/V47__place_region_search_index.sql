-- Region-filtered place search scans this index alone (LIKE on search_name) instead of reading rows, and
-- the exact-name lookup uses it with the full region code list. It starts with the same columns as
-- idx_place_region_active, so that index (and its share of the small buffer pool) goes, and
-- fk_place_region keeps an index led by region_code. Online: imports and post writes keep running.
ALTER TABLE place
    ADD INDEX idx_place_region_search (region_code, active, search_name),
    DROP INDEX idx_place_region_active,
    ALGORITHM = INPLACE, LOCK = NONE;
