-- personality is now a required preference (see UserUpdateRequest/RegistrationRequest).
-- Users created before this change may have zero PERSONALITY rows; backfill with an
-- arbitrary value (data is test-only, per product decision) so profile reads don't 404.
INSERT INTO user_preference (user_id, preference_id)
SELECT u.id, (SELECT id FROM preference WHERE type = 'PERSONALITY' AND name = 'INTROVERTED')
FROM user u
WHERE NOT EXISTS (
    SELECT 1
    FROM user_preference up
             JOIN preference p ON p.id = up.preference_id
    WHERE up.user_id = u.id
      AND p.type = 'PERSONALITY'
);
