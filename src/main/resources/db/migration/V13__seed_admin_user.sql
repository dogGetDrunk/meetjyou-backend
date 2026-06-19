INSERT INTO user (uuid, email, nickname, auth_provider, external_id, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'admin@meetjyou.local', '관리자', 'KAKAO', 'admin-system', 'ADMIN')
ON DUPLICATE KEY UPDATE role = 'ADMIN';
