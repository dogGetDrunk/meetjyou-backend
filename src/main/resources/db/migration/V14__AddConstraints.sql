DELETE up1 FROM user_preference up1
    INNER JOIN user_preference up2
        ON up1.user_id = up2.user_id AND up1.preference_id = up2.preference_id AND up1.id > up2.id;

DELETE cp1 FROM comp_preference cp1
    INNER JOIN comp_preference cp2
        ON cp1.post_id = cp2.post_id AND cp1.preference_id = cp2.preference_id AND cp1.id > cp2.id;

ALTER TABLE user_preference ADD CONSTRAINT uq_user_preference UNIQUE (user_id, preference_id);
ALTER TABLE comp_preference ADD CONSTRAINT uq_comp_preference UNIQUE (post_id, preference_id);

ALTER TABLE comp_preference DROP FOREIGN KEY comp_preference_ibfk_1;
ALTER TABLE comp_preference ADD CONSTRAINT fk_comp_preference_post
    FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE;
