-- =========================================================
-- Dummy seed data for local API testing
-- Assumption: loaded after schema.sql on an empty database
-- =========================================================

-- policy data: preference
INSERT INTO preference (type, name)
VALUES ('GENDER', 'M'),
       ('GENDER', 'F'),
       ('GENDER', 'O'),
       ('AGE', 'TEEN'),
       ('AGE', 'TWENTY'),
       ('AGE', 'THIRTY'),
       ('AGE', 'FORTY'),
       ('AGE', 'OLDER'),
       ('PERSONALITY', 'INTROVERTED'),
       ('PERSONALITY', 'EXTROVERTED'),
       ('PERSONALITY', 'SOCIAL'),
       ('PERSONALITY', 'OPTIMISTIC'),
       ('PERSONALITY', 'FREE'),
       ('PERSONALITY', 'PRACTICAL'),
       ('PERSONALITY', 'CAREFUL'),
       ('PERSONALITY', 'BOLD'),
       ('TRAVEL_STYLE', 'ACTIVITY'),
       ('TRAVEL_STYLE', 'RELAX'),
       ('TRAVEL_STYLE', 'CULTURE'),
       ('TRAVEL_STYLE', 'FOOD'),
       ('TRAVEL_STYLE', 'SPORTS'),
       ('TRAVEL_STYLE', 'BUDGET'),
       ('TRAVEL_STYLE', 'LUXURY'),
       ('TRAVEL_STYLE', 'ADVENTURE'),
       ('TRAVEL_STYLE', 'NATURE'),
       ('TRAVEL_STYLE', 'URBAN'),
       ('TRAVEL_STYLE', 'ART'),
       ('TRAVEL_STYLE', 'SHOP'),
       ('DIET', 'VEGETARIAN'),
       ('DIET', 'GLUTEN_FREE'),
       ('DIET', 'VEGAN'),
       ('DIET', 'SPECIFIC'),
       ('DIET', 'ANYTHING'),
       ('ETC', 'SMOKE'),
       ('ETC', 'NOT_SMOKE'),
       ('ETC', 'DRINK'),
       ('ETC', 'NOT_DRINK'),
       ('ETC', 'DONT_CARE');

-- policy/runtime baseline data: terms
INSERT INTO terms
    (uuid, type, version, display_text, required, content_object_key, content_hash, status, effective_at)
VALUES ('44a42621-11bb-4ab3-a101-b0e2d164edf0', 'AGE_OVER_14', '1.0', '만 14세 이상입니다.', true,
        'terms/age/v1.0.html', '17e443e57b552617372fbb7edd3522a8a5d9edec1f025ddbf197435de6feb2a2', 'ACTIVE', NOW()),
       ('fe7d4bc0-5b2b-458e-9ce3-79f7e5b531d3', 'TERMS_OF_SERVICE', '1.0', '서비스 이용 약관 동의', true,
        'terms/service/v1.0.html', '6948ae9eae7dcfb37557633c46e4ba7d23066105d6d5be28d0b9c3a7c7aec616', 'ACTIVE', NOW()),
       ('57d75a47-bbc4-4741-8337-02555061c60f', 'PRIVACY_COLLECTION_USE', '1.0', '개인정보 수집 및 이용 동의', true,
        'terms/privacy/v1.0.html', '4c28d06b17ed9e26a4571c3f583229c393a3e1f112c6b2c7c4369d9dbb33589d', 'ACTIVE', NOW()),
       ('bdbdeb83-6ac8-44a7-93c0-5b8ecabd3af1', 'MARKETING_SNS_EVENTS', '1.0', 'SNS 이벤트 등 마케팅 수신 동의', false,
        'terms/marketing-sns/v1.0.html', '5a5c83c3fb041bedce9e5699b8fc742c1ccb6b119ca8732c0227a1c46ec1f50b', 'ACTIVE', NOW()),
       ('d03a97dc-fb87-4ac2-8aa3-660263aa9dd8', 'MARKETING_EMAIL_EVENTS', '1.0', '이메일 이벤트 등 마케팅 수신 동의', false,
        'terms/marketing-email/v1.0.html', '5ce1fd6e3082ad76d1f6aa7ba63eb5a3e27dcf717bbfb782aa8a42d039f9fc35', 'ACTIVE', NOW());

-- users
INSERT INTO user
    (uuid, email, nickname, auth_provider, external_id, role, bio, participation, status, img_url, thumb_img_url, notified,
     last_login_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'userA@test.com', 'userA', 'KAKAO', 'ext-user-a', 'USER',
        '서울 맛집과 산책을 좋아합니다.', 2, 'NORMAL', 'https://example.com/userA.jpg', 'https://example.com/userA-thumb.jpg', true,
        NOW(), NOW()),
       ('22222222-2222-2222-2222-222222222222', 'userB@test.com', 'userB', 'GOOGLE', 'ext-user-b', 'USER',
        '전시회와 카페 투어를 자주 갑니다.', 1, 'NORMAL', 'https://example.com/userB.jpg', 'https://example.com/userB-thumb.jpg', true,
        NOW(), NOW()),
       ('33333333-3333-3333-3333-333333333333', 'userC@test.com', 'userC', 'APPLE', 'ext-user-c', 'USER',
        '즉흥 여행과 사진 찍는 걸 좋아합니다.', 3, 'NORMAL', 'https://example.com/userC.jpg', 'https://example.com/userC-thumb.jpg', false,
        NOW(), NOW());

-- all users agree to current terms
INSERT INTO user_terms (user_id, terms_id, agreed_at)
VALUES (1, 1, NOW()),
       (1, 2, NOW()),
       (1, 3, NOW()),
       (2, 1, NOW()),
       (2, 2, NOW()),
       (2, 3, NOW()),
       (3, 1, NOW()),
       (3, 2, NOW()),
       (3, 3, NOW()),
       (3, 4, NOW());

-- user preferences
INSERT INTO user_preference (user_id, preference_id)
VALUES (1, 2),
       (1, 5),
       (1, 14),
       (1, 20),
       (1, 35),
       (1, 36),
       (2, 1),
       (2, 6),
       (2, 11),
       (2, 27),
       (2, 29),
       (2, 37),
       (3, 3),
       (3, 5),
       (3, 16),
       (3, 24),
       (3, 33),
       (3, 38);

-- plans
INSERT INTO plan (uuid, itin_start, itin_finish, destination, center_lat, center_lng, memo, owner_id)
VALUES ('aaaa1111-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 9 DAY), '서울',
        37.5665000000, 126.9780000000, '서울 2박 3일 먹방 여행', 1),
       ('aaaa2222-aaaa-aaaa-aaaa-aaaaaaaaaaa2', DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 16 DAY), '부산',
        35.1796000000, 129.0756000000, '부산 바다와 카페 일정', 2),
       ('aaaa3333-aaaa-aaaa-aaaa-aaaaaaaaaaa3', DATE_ADD(NOW(), INTERVAL 21 DAY), DATE_ADD(NOW(), INTERVAL 24 DAY), '제주',
        33.4996000000, 126.5312000000, '제주 드라이브 여행', 3);

-- markers
INSERT INTO marker (uuid, lat, lng, date, day_num, idx, place, memo, plan_id)
VALUES ('bbbb1111-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 37.5704000000, 126.9920000000, DATE_ADD(NOW(), INTERVAL 7 DAY), 1, 1, '인사동',
        '점심 후 산책', 1),
       ('bbbb1112-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 37.5512000000, 126.9882000000, DATE_ADD(NOW(), INTERVAL 8 DAY), 2, 1, '남산타워',
        '야경 보기', 1),
       ('bbbb2221-bbbb-bbbb-bbbb-bbbbbbbbbbb3', 35.1587000000, 129.1603000000, DATE_ADD(NOW(), INTERVAL 14 DAY), 1, 1, '해운대',
        '해변 산책', 2),
       ('bbbb3331-bbbb-bbbb-bbbb-bbbbbbbbbbb4', 33.4507000000, 126.5707000000, DATE_ADD(NOW(), INTERVAL 21 DAY), 1, 1, '한라산',
        '가벼운 트레킹', 3);

-- parties
INSERT INTO party
    (uuid, itin_start, itin_finish, destination, joined, capacity, name, progress_status, recruitment_status, created_at,
     last_edited_at, plan_id)
VALUES ('cccc1111-cccc-cccc-cccc-ccccccccccc1', DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 9 DAY), '서울', 2, 4,
        '서울 주말 동행', 'PLANNING', 'OPEN', NOW(), NOW(), 1),
       ('cccc2222-cccc-cccc-cccc-ccccccccccc2', DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 16 DAY), '부산', 1, 3,
        '부산 카페 투어', 'PLANNING', 'OPEN', NOW(), NOW(), 2);

-- party members
INSERT INTO user_party (role, member_status, party_id, user_id, last_read_message_id)
VALUES ('HOST', 'JOINED', 1, 1, NULL),
       ('MEMBER', 'JOINED', 1, 2, NULL),
       ('HOST', 'JOINED', 2, 2, NULL);

-- chat rooms
INSERT INTO chat_room (room_id, uuid)
VALUES (1, 'dddd1111-dddd-dddd-dddd-ddddddddddd1'),
       (2, 'dddd2222-dddd-dddd-dddd-ddddddddddd2');

-- chat participants
INSERT INTO chat_participant (user_id, room_id, last_read_at)
VALUES (1, 1, NULL),
       (2, 1, NULL),
       (2, 2, NULL);

-- chat messages
INSERT INTO chat_message (uuid, room_id, sender_id, body, created_at)
VALUES ('eeee1111-eeee-eeee-eeee-eeeeeeeeeee1', 1, 1, '서울 여행 같이 가실 분 구해요.', NOW()),
       ('eeee1112-eeee-eeee-eeee-eeeeeeeeeee2', 1, 2, '일정 괜찮아요. 같이 이야기해봐요.', NOW()),
       ('eeee2221-eeee-eeee-eeee-eeeeeeeeeee3', 2, 2, '부산 파티는 카페 위주로 생각 중입니다.', NOW());

-- posts
INSERT INTO post
    (uuid, title, content, is_instant, views, capacity, joined, itin_start, itin_finish, location, created_at, last_edited_at,
     status, is_plan_public, author_id, party_id, plan_id)
VALUES ('ffff1111-ffff-ffff-ffff-fffffffffff1', '서울 주말 동행 구해요', '맛집, 산책 중심으로 서울 같이 다닐 분을 구합니다.', false, 12, 4, 2,
        DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 9 DAY), '서울', NOW(), NOW(), 'RECRUITING', true, 1, 1, 1),
       ('ffff2222-ffff-ffff-ffff-fffffffffff2', '부산 카페 투어 모집', '해운대 근처 카페와 바다 산책 일정입니다.', false, 5, 3, 1,
        DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 16 DAY), '부산', NOW(), NOW(), 'RECRUITING', true, 2, 2, 2),
       ('ffff3333-ffff-ffff-ffff-fffffffffff3', '제주 드라이브 메이트', '즉흥적으로 제주 한 바퀴 돌 예정입니다.', true, 3, 2, 1,
        DATE_ADD(NOW(), INTERVAL 21 DAY), DATE_ADD(NOW(), INTERVAL 24 DAY), '제주', NOW(), NOW(), 'RECRUITING', false, 3, NULL, 3);

-- post compatibility preferences
INSERT INTO comp_preference (post_id, preference_id)
VALUES (1, 5),
       (1, 20),
       (1, 35),
       (2, 6),
       (2, 27),
       (2, 37),
       (3, 24),
       (3, 33),
       (3, 38);

-- applications
INSERT INTO party_application (title, body, status, created_at, party_id, user_id)
VALUES ('서울 주말 동행 신청', '맛집 위주 일정이면 함께하고 싶습니다.', 0, NOW(), 1, 3);

-- notifications
INSERT INTO notification (uuid, type, created_at, message, reference_id, isRead, user_id)
VALUES ('99991111-9999-9999-9999-999999999991', 0, NOW(), '새로운 파티 신청이 도착했습니다.', 1, false, 1),
       ('99992222-9999-9999-9999-999999999992', 0, NOW(), '새 메시지가 도착했습니다.', 1, false, 2);

-- notices
INSERT INTO notice (uuid, title, body, created_at, last_edited_at)
VALUES ('88881111-8888-8888-8888-888888888881', '테스트 공지', '로컬 API 테스트용 공지 데이터입니다.', NOW(), NOW()),
       ('88882222-8888-8888-8888-888888888882', '점검 안내', '주기적인 점검이 예정되어 있습니다.', NOW(), NOW());

-- app version and push tokens
INSERT INTO app_version (version, force_update, download_url, released_at)
VALUES ('1.0.0', false, 'https://example.com/download/v1.0.0', NOW());

INSERT INTO push_token (uuid, token, platform, device_model, is_active, last_updated_at, created_at, user_id, app_version_id)
VALUES ('77771111-7777-7777-7777-777777777771', 'push-token-user-a', 'ANDROID', 'Pixel 8', true, NOW(), NOW(), 1, 1),
       ('77772222-7777-7777-7777-777777777772', 'push-token-user-b', 'IOS', 'iPhone 15', true, NOW(), NOW(), 2, 1);

-- notification outbox
INSERT INTO notification_outbox
    (uuid, type, title, body, data_json, dedup_key, status, attempts, available_at, created_at, user_id)
VALUES ('66661111-6666-6666-6666-666666666661', 'CHAT_MESSAGE', '새 메시지', '서울 주말 동행 채팅방에 새 메시지가 있습니다.',
        JSON_OBJECT('roomId', 1, 'partyId', 1), 'chat-room-1-message-1', 'PENDING', 0, NOW(), NOW(), 1);
