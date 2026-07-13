-- Before e7f7f78 (2026-05-18), party creation did not create a matching chat_room row.
-- Parties created prior to that fix have no chat_room, which crashes getMyParties().
-- Backfill a chat_room for every party that is still missing one.
INSERT INTO chat_room (room_id, uuid)
SELECT p.id, UUID()
FROM party p
         LEFT JOIN chat_room cr ON cr.room_id = p.id
WHERE cr.room_id IS NULL;
