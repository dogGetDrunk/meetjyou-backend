ALTER TABLE user
    ADD COLUMN has_profile_image BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user
SET has_profile_image = (img_url IS NOT NULL);

ALTER TABLE user
    DROP COLUMN img_url,
    DROP COLUMN thumb_img_url;
