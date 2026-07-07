CREATE TABLE platform_store_url
(
    platform     VARCHAR(20)  NOT NULL PRIMARY KEY,
    download_url VARCHAR(500) NOT NULL
);

INSERT INTO platform_store_url (platform, download_url)
SELECT platform, download_url
FROM (
    SELECT platform,
           download_url,
           ROW_NUMBER() OVER (PARTITION BY platform ORDER BY released_at DESC) AS rn
    FROM app_version
) ranked
WHERE rn = 1;

ALTER TABLE app_version DROP COLUMN download_url;

ALTER TABLE app_version ADD COLUMN message VARCHAR(500) NULL;
