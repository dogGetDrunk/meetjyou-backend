DELETE FROM comp_preference WHERE post_id IN (SELECT id FROM post WHERE party_id IS NULL);
DELETE FROM post WHERE party_id IS NULL;
ALTER TABLE post MODIFY party_id INT NOT NULL;