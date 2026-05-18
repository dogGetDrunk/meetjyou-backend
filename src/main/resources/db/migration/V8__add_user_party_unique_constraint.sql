-- Step 1: delete duplicate user_party rows per (user_id, party_id).
-- Priority: JOINED(1) > PENDING(2) > REJECTED(3) > LEFT(4) > BANNED(5)
-- Tie-break: keep the row with the highest id (most recently inserted).
DELETE up1
FROM user_party up1
         INNER JOIN user_party up2
                    ON up1.user_id = up2.user_id
                        AND up1.party_id = up2.party_id
                        AND up1.id <> up2.id
WHERE (
          CASE up1.member_status
              WHEN 'JOINED'   THEN 1
              WHEN 'PENDING'  THEN 2
              WHEN 'REJECTED' THEN 3
              WHEN 'LEFT'     THEN 4
              WHEN 'BANNED'   THEN 5
              ELSE 6
              END
          >
          CASE up2.member_status
              WHEN 'JOINED'   THEN 1
              WHEN 'PENDING'  THEN 2
              WHEN 'REJECTED' THEN 3
              WHEN 'LEFT'     THEN 4
              WHEN 'BANNED'   THEN 5
              ELSE 6
              END
          )
   OR (
          up1.member_status = up2.member_status
          AND up1.id < up2.id
      );

-- Step 2: enforce uniqueness going forward.
ALTER TABLE user_party
    ADD CONSTRAINT uk_user_party UNIQUE (user_id, party_id);
