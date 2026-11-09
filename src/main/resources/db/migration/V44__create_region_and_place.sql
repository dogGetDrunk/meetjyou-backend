-- Travel destinations: a region (required) plus an optional place.
-- region: 시·도 (2-digit) > 시·군·구 (5-digit 법정동 code); general-gu (일반구) hang under their city.
CREATE TABLE region
(
    code        VARCHAR(5)  NOT NULL PRIMARY KEY,
    name        VARCHAR(30) NOT NULL,
    full_name   VARCHAR(60) NOT NULL,
    level       VARCHAR(10) NOT NULL,
    parent_code VARCHAR(5)  NULL,
    searchable  TINYINT(1)  NOT NULL,
    CONSTRAINT fk_region_parent FOREIGN KEY (parent_code) REFERENCES region (code)
);

-- place rows are never deleted: posts keep pointing at a store that left the source file
-- (active = 0, shown as "no current business info").
CREATE TABLE place
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    source        VARCHAR(20)  NOT NULL,
    source_key    VARCHAR(30)  NOT NULL,
    name          VARCHAR(200) NOT NULL,
    branch_name   VARCHAR(100) NULL,
    search_name   VARCHAR(300) NOT NULL,
    category_code VARCHAR(10)  NOT NULL,
    category_name VARCHAR(50)  NOT NULL,
    region_code   VARCHAR(5)   NOT NULL,
    road_address  VARCHAR(200) NOT NULL,
    lat           DOUBLE       NOT NULL,
    lng           DOUBLE       NOT NULL,
    active        TINYINT(1)   NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Bumped by MySQL only when a column value really changes, so an unchanged re-import leaves it alone.
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_place_source_key UNIQUE (source, source_key),
    CONSTRAINT fk_place_region FOREIGN KEY (region_code) REFERENCES region (code),
    INDEX idx_place_region_active (region_code, active),
    -- Prefix index only narrows rematch candidates; equality is checked on the full values.
    INDEX idx_place_rematch (source, region_code, search_name(100), road_address(100)),
    FULLTEXT INDEX ft_place_search_name (search_name) WITH PARSER ngram
);

-- One import run's filtered rows. Emptied at the start of every run; left as-is after a failure.
CREATE TABLE place_import_staging
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_key    VARCHAR(30)  NOT NULL,
    name          VARCHAR(200) NOT NULL,
    branch_name   VARCHAR(100) NULL,
    search_name   VARCHAR(300) NOT NULL,
    category_code VARCHAR(10)  NOT NULL,
    category_name VARCHAR(50)  NOT NULL,
    region_code   VARCHAR(5)   NOT NULL,
    sido_code     VARCHAR(2)   NOT NULL,
    road_address  VARCHAR(200) NOT NULL,
    lat           DOUBLE       NOT NULL,
    lng           DOUBLE       NOT NULL,
    UNIQUE INDEX uk_staging_source_key (source_key),
    INDEX idx_staging_rematch (region_code, search_name(100), road_address(100))
);
