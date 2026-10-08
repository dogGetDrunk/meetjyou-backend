-- A user is identified by (auth_provider, external_id); email is contact data taken from the
-- verified provider token. Uniqueness blocked the same person from signing up with a second
-- provider and let anyone squat someone else's email under the old body-supplied email.
ALTER TABLE user DROP INDEX email;
