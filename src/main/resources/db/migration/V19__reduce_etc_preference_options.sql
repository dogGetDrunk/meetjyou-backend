DELETE up FROM user_preference up
    JOIN preference p ON p.id = up.preference_id
    WHERE p.type = 'ETC' AND p.name IN ('NOT_SMOKE', 'NOT_DRINK');

DELETE cp FROM comp_preference cp
    JOIN preference p ON p.id = cp.preference_id
    WHERE p.type = 'ETC' AND p.name IN ('NOT_SMOKE', 'NOT_DRINK');

DELETE FROM preference WHERE type = 'ETC' AND name IN ('NOT_SMOKE', 'NOT_DRINK');
