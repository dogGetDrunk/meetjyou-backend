-- V22 added plan.title with DEFAULT '' for pre-existing rows.
-- Backfill a readable title: prefer the linked post's title (user-authored,
-- already length-bounded to fit), fall back to the plan's destination.
UPDATE plan p
    JOIN post po ON po.plan_id = p.id
    SET p.title = LEFT(po.title, 20)
    WHERE p.title = '';

UPDATE plan
    SET title = LEFT(destination, 20)
    WHERE title = '';
