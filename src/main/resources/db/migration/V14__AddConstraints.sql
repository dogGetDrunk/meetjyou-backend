ALTER TABLE user_preference ADD CONSTRAINT uq_user_preference UNIQUE (user_id, preference_id);
ALTER TABLE comp_preference ADD CONSTRAINT uq_comp_preference UNIQUE (post_id, preference_id);

ALTER TABLE comp_preference DROP FOREIGN KEY comp_preference_ibfk_1;
ALTER TABLE comp_preference ADD CONSTRAINT fk_comp_preference_post
    FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE;
