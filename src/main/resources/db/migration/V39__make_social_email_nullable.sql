-- Email is optional at every provider (Kakao consent is optional; Google/Apple may report it
-- unverified). It is contact data, not identity (V36, V37), so an account without one must still
-- be able to sign up and log in.
ALTER TABLE user MODIFY email VARCHAR(255) NULL;
ALTER TABLE user_social_account MODIFY email VARCHAR(255) NULL;

-- Purged rows held '' as a stand-in for "no email"; use NULL like every other email-less row.
UPDATE user SET email = NULL WHERE status = 'PURGED';
