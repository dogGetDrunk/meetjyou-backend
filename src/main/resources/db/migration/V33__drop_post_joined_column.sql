-- post.joined was a duplicate of party.joined that was never updated after post creation,
-- causing the joined count shown on post views to stay frozen at its initial value.
ALTER TABLE post
    DROP COLUMN joined;
