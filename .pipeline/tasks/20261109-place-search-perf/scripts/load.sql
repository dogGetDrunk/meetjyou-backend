CREATE TABLE raw_place (id BIGINT AUTO_INCREMENT PRIMARY KEY, source_key VARCHAR(30), name VARCHAR(200), branch_name VARCHAR(100), search_name VARCHAR(300), category_code VARCHAR(10), category_name VARCHAR(50), region_code VARCHAR(5), road_address VARCHAR(200), lat DOUBLE, lng DOUBLE);
LOAD DATA INFILE '/var/lib/mysql-files/places.tsv' INTO TABLE raw_place CHARACTER SET utf8mb4 FIELDS TERMINATED BY '\t' (source_key, name, branch_name, search_name, category_code, category_name, region_code, road_address, lat, lng);
DELIMITER //
CREATE PROCEDURE fill() BEGIN
  DECLARE lo BIGINT DEFAULT 1; DECLARE hi BIGINT; SELECT MAX(id) INTO hi FROM raw_place;
  WHILE lo <= hi DO
    INSERT INTO place (source, source_key, name, branch_name, search_name, category_code, category_name, region_code, road_address, lat, lng, active)
    SELECT 'SBIZ', source_key, name, branch_name, search_name, category_code, category_name, region_code, road_address, lat, lng, 1 FROM raw_place WHERE id BETWEEN lo AND lo + 4999;
    COMMIT; SET lo = lo + 5000;
  END WHILE; END//
DELIMITER ;
SET autocommit = 1;
CALL fill();
DROP TABLE raw_place;
SELECT COUNT(*) FROM place;
