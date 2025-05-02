-- user
INSERT INTO user (uuid, email, nickname, birth_date, bio, participation, img_url, thumb_img_url, notified,
                  last_login_at, updated_at, auth_provider, status, role)
VALUES ('6faff71a-52ac-41e7-aa48-64a021f829fa', 'alice@example.com', '김민지', '1995-06-15', '여행과 독서를 좋아합니다.', 5,
        'https://example.com/img1.jpg', 'https://example.com/thumb1.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'USER'),
       ('70bff056-fe9c-4416-9c0b-a122c84bedaa', 'bob@example.com', '이준호', '1998-02-20', '', 3,
        'https://example.com/img2.jpg', 'https://example.com/thumb2.jpg', 1, NOW(), NOW(), 'GOOGLE', 'NORMAL', 'ADMIN'),
       ('55f5807e-fea1-4135-a165-689c6155a190', 'charlie@example.com', '박서연', '2000-11-11', '사진 촬영이 취미입니다.', 8,
        'https://example.com/img3.jpg', 'https://example.com/thumb3.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'USER'),
       ('1ba03218-5617-40ed-a3fa-595583a5dcd4', 'dave@example.com', '최민수', '1997-04-05', '도시 탐방을 즐깁니다.', 6,
        'https://example.com/img4.jpg', 'https://example.com/thumb4.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'ADMIN'),
       ('1f83329d-47bf-4a85-a25d-33587fe0c475', 'eva@example.com', '정다은', '1993-09-23', '요가와 명상을 좋아합니다.', 2,
        'https://example.com/img5.jpg', 'https://example.com/thumb5.jpg', 1, NOW(), NOW(), 'GOOGLE', 'NORMAL', 'USER'),
       ('59d901f6-2f8d-4c2f-afe0-2b7c9dc1ed3e', 'frank@example.com', '한지훈', '1995-12-30', '', 4,
        'https://example.com/img6.jpg', 'https://example.com/thumb6.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'ADMIN'),
       ('cccac2a7-58d6-4c57-91b6-9f14e364b42a', 'grace@example.com', '김수현', '2001-05-21', '캠핑과 하이킹을 좋아합니다.', 9,
        'https://example.com/img7.jpg', 'https://example.com/thumb7.jpg', 1, NOW(), NOW(), 'GOOGLE', 'NORMAL', 'USER'),
       ('2d58f7e2-e290-490c-a84c-74c8ac7fefba', 'henry@example.com', '이서준', '1999-07-14', '배낭여행을 준비 중!', 7,
        'https://example.com/img8.jpg', 'https://example.com/thumb8.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'ADMIN'),
       ('c37d126c-dbe0-41f5-b293-94b2311b3a1a', 'isabel@example.com', '박하늘', '1996-10-02', '도전하는 삶!', 5,
        'https://example.com/img9.jpg', 'https://example.com/thumb9.jpg', 1, NOW(), NOW(), 'GOOGLE', 'NORMAL', 'USER'),
       ('e4fe1a8b-8750-49e7-96f2-9e50c14765f3', 'jack@example.com', '최예린', '1994-08-08', '세상을 탐험 중', 1,
        'https://example.com/img10.jpg', 'https://example.com/thumb10.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL',
        'ADMIN');

-- preference 테이블 더미 데이터 삽입
INSERT INTO preference (type, name)
VALUES (0, 'M'),
       (0, 'F'),
       (0, 'O'),
       (1, 'TEEN'),
       (1, 'TWENTY'),
       (1, 'THIRTY'),
       (1, 'FORTY'),
       (1, 'OLDER'),
       (2, 'INTROVERTED'),
       (2, 'EXTROVERTED'),
       (2, 'SOCIAL'),
       (2, 'OPTIMISTIC'),
       (2, 'FREE'),
       (2, 'PRACTICAL'),
       (2, 'CAREFUL'),
       (2, 'BOLD'),
       (3, 'ACTIVITY'),
       (3, 'RELAX'),
       (3, 'CULTURE'),
       (3, 'FOOD'),
       (3, 'SPORTS'),
       (3, 'BUDGET'),
       (3, 'LUXURY'),
       (3, 'ADVENTURE'),
       (3, 'NATURE'),
       (3, 'URBAN'),
       (3, 'ART'),
       (3, 'SHOP'),
       (4, 'VEGETARIAN'),
       (4, 'GLUTEN_FREE'),
       (4, 'VEGAN'),
       (4, 'SPECIFIC'),
       (4, 'ANYTHING'),
       (5, 'SMOKE'),
       (5, 'NOT_SMOKE'),
       (5, 'DRINK'),
       (5, 'NOT_DRINK'),
       (5, 'ANYTHING');

INSERT INTO user_preference (user_id, preference_id)
VALUES
    -- 김민지: F, TWENTY, PRACTICAL, NATURE, FOOD
    (1, 2),   -- F
    (1, 5),   -- TWENTY
    (1, 14),  -- PRACTICAL
    (1, 25),  -- NATURE
    (1, 33),  -- ANYTHING
    (1, 38),  -- ANYTHING

    -- 이준호: M, TWENTY, EXTROVERTED, ADVENTURE, SPECIFIC
    (2, 1),   -- M
    (2, 5),   -- TWENTY
    (2, 10),  -- EXTROVERTED
    (2, 24),  -- ADVENTURE
    (2, 32),  -- SPECIFIC
    (2, 34),  -- SMOKE

    -- 박서연: F, TWENTY, SOCIAL, ART, VEGAN
    (3, 2),   -- F
    (3, 5),   -- TWENTY
    (3, 11),  -- SOCIAL
    (3, 27),  -- ART
    (3, 31),  -- VEGAN
    (3, 35),  -- NOT_SMOKE
    (3, 36),  -- DRINK

    -- 최민수: M, THIRTY, OPTIMISTIC, SHOP, GLUTEN_FREE
    (4, 1),   -- M
    (4, 6),   -- THIRTY
    (4, 12),  -- OPTIMISTIC
    (4, 28),  -- SHOP
    (4, 30),  -- GLUTEN_FREE
    (4, 35),  -- NOT_SMOKE
    (4, 36),  -- DRINK

    -- 정다은: F, THIRTY, FREE, NATURE, VEGETARIAN
    (5, 2),   -- F
    (5, 6),   -- THIRTY
    (5, 13),  -- FREE
    (5, 25),  -- NATURE
    (5, 29),  -- VEGETARIAN
    (5, 34),  -- SMOKE
    (5, 36),  -- DRINK

    -- 한지훈: M, THIRTY, INTROVERTED, URBAN, ANYTHING
    (6, 1),   -- M
    (6, 6),   -- THIRTY
    (6, 9),   -- INTROVERTED
    (6, 26),  -- URBAN
    (6, 33),  -- ANYTHING (ANYTHING은 한 개만 선택 가능)
    (6, 38),  -- ANYTHING

    -- 김수현: F, TWENTY, CAREFUL, RELAX, MEAT
    (7, 2),   -- F
    (7, 5),   -- TWENTY
    (7, 15),  -- CAREFUL
    (7, 18),  -- RELAX
    (7, 33),  -- ANYTHING
    (7, 38),  -- ANYTHING

    -- 이서준: M, TWENTY, BOLD, ACTIVITY, SPECIFIC
    (8, 1),   -- M
    (8, 5),   -- TWENTY
    (8, 16),  -- BOLD
    (8, 17),  -- ACTIVITY
    (8, 32),  -- SPECIFIC
    (8, 35),  -- NOT_SMOKE
    (8, 37),  -- NOT_DRINK

    -- 박하늘: F, THIRTY, SOCIAL, FOOD, VEGETARIAN
    (9, 2),   -- F
    (9, 6),   -- THIRTY
    (9, 11),  -- SOCIAL
    (9, 19),  -- FOOD
    (9, 29),  -- VEGETARIAN
    (9, 35),  -- NOT_SMOKE
    (9, 37),  -- NOT_DRINK

    -- 최예린: M, THIRTY, FREE, ART, VEGAN
    (10, 1),  -- M
    (10, 6),  -- THIRTY
    (10, 13), -- FREE
    (10, 27), -- ART
    (10, 30), -- GLUTEN_FREE
    (10, 35), -- NOT_SMOKE
    (10, 36);
-- DRINK

INSERT INTO post (uuid, title, content, views, capacity, itin_start, itin_finish, location, created_at, last_edited_at,
                  post_status, author_id, party_id, plan_id)
VALUES ('5fd1c71b-d189-420a-a071-3bd3654c928f', '첫 번째 여행 모집', '함께 여행 가실 분 모집합니다!', 23, 3, NOW(),
        DATE_ADD(NOW(), INTERVAL 3 DAY), '서울', NOW(), NOW(), 1, 1, NULL, NULL),
       ('d08cc69f-4608-4f9f-9b7b-02e328cf04e2', '서울 맛집 탐방', '서울에서 가장 맛있는 음식점을 공유해요.', 45, 2, NOW(),
        DATE_ADD(NOW(), INTERVAL 1 DAY), '서울', NOW(), NOW(), 1, 2, NULL, NULL),
       ('dd93f292-0cc4-4a49-a821-2b10d13b252f', '배낭여행 정보 공유', '배낭여행 팁과 노하우를 공유합니다.', 67, 5, NOW(),
        DATE_ADD(NOW(), INTERVAL 5 DAY), '유럽', NOW(), NOW(), 1, 3, NULL, NULL),
       ('0c4b1403-1c4e-4e83-b85c-2e92dc90bc0a', '혼자 여행 꿀팁', '혼자 여행할 때 유용한 정보를 제공합니다.', 12, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 1 DAY), '전국', NOW(), NOW(), 1, 4, NULL, NULL),
       ('ab6a40be-51c3-4a4f-8324-3bbd365b1c8f', '새로운 여행 동반자 구함', '여행을 함께할 친구를 찾아요.', 34, 4, NOW(),
        DATE_ADD(NOW(), INTERVAL 2 DAY), '부산', NOW(), NOW(), 1, 5, NULL, NULL),
       ('04276a3d-12cb-40aa-b7cc-309e6f3d87e1', '유럽 여행 후기', '유럽 여행에서 가장 좋았던 경험을 공유합니다.', 89, 6, NOW(),
        DATE_ADD(NOW(), INTERVAL 10 DAY), '프랑스', NOW(), NOW(), 1, 6, NULL, NULL),
       ('1639b51d-7e6d-430f-b394-f6b67c914a47', '국내 여행 추천', '국내에서 꼭 가봐야 할 곳을 추천합니다.', 78, 3, NOW(),
        DATE_ADD(NOW(), INTERVAL 3 DAY), '제주도', NOW(), NOW(), 1, 7, NULL, NULL),
       ('7d6f6e82-65c3-4a17-a9f4-84df83c999a4', '여행 사진 공유', '여행 중 찍은 사진을 공유해요.', 56, 2, NOW(),
        DATE_ADD(NOW(), INTERVAL 2 DAY), '전국', NOW(), NOW(), 1, 8, NULL, NULL),
       ('5090610f-b9f9-4934-a6f3-6b92d28ab140', '여행 비용 절약 팁', '여행 경비를 아끼는 다양한 방법!', 34, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 1 DAY), '일본', NOW(), NOW(), 1, 9, NULL, NULL),
       ('07b60b2b-02e0-4e0d-9f3f-001157d226e4', '일본 여행 계획', '일본 여행 일정과 계획을 공유합니다.', 22, 3, NOW(),
        DATE_ADD(NOW(), INTERVAL 4 DAY), '일본', NOW(), NOW(), 1, 10, NULL, NULL);

-- party
INSERT INTO party (uuid, itin_start, itin_finish, destination, joined, max, name, img_url, thumb_img_url, created_at,
                   last_edited_at, plan_id)
VALUES ('68932cf4-8550-476a-a9d8-210f8fe9c21a', NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), '부산', 1, 5, '부산여행친구',
        'https://example.com/party.jpg', 'https://example.com/party_thumb.jpg', NOW(), NOW(), NULL);

-- plan
INSERT INTO plan (uuid, itin_start, itin_finish, destination, center_lat, center_lng, memo, user_id)
VALUES ('d3ad1342-21bc-41e6-93f1-9f16bb9b9e38', NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), '서울', 37.5665, 126.9780,
        '서울 투어 일정', 1);

-- marker
INSERT INTO marker (uuid, lat, lng, day, place, memo, plan_id)
VALUES ('127c6fc0-3ecf-441c-b92d-33a96c92f924', 37.5665, 126.9780, 1, '경복궁', '경복궁 투어', 1);

-- notification
INSERT INTO notification (uuid, type, created_at, message, reference_id, isRead, user_id)
VALUES ('493c29b2-d69c-46e7-9bd7-06b8ec9b3609', 0, NOW(), '새로운 모집글이 등록되었습니다.', 1, 0, 1);

-- notice
INSERT INTO notice (uuid, title, body)
VALUES ('ea178f8d-0e49-4a6e-a325-d36b64e06457', '서비스 점검 안내',
        '안녕하세요. 보다 나은 서비스를 제공하기 위해 3월 30일 01:00 ~ 05:00까지 점검이 진행됩니다.'),
       ('ae5d66dc-1d57-464f-9221-d4b15224b54e', '신규 기능 출시', '프로필 커스터마이징 기능이 새롭게 추가되었습니다. 지금 확인해보세요!'),
       ('97bfae84-c5cf-4cf3-94a8-1652df6dbbe3', '이벤트 당첨자 발표', '3월 친구 초대 이벤트 당첨자를 발표합니다. 자세한 내용은 공지사항을 확인해주세요.'),
       ('f3fcd7a2-b989-4b4b-a356-50fa4f3ae284', '앱 업데이트 안내', '최신 버전으로 업데이트하시면 더욱 안정적인 사용이 가능합니다.'),
       ('c034a2db-68e2-4e62-9123-5d0541a387c2', '정책 변경 사전 안내', '개인정보 처리방침이 4월 1일부터 변경됩니다. 필히 확인 부탁드립니다.'),
       ('7a8a5cb2-36f2-4e29-a882-e4e3be769162', '비정상 활동 제한', '비정상적인 활동이 감지된 계정에 대해 이용이 제한되었습니다. 문의는 고객센터로 부탁드립니다.'),
       ('bdfc5a90-02be-4a6e-9e97-ec2020d11dd7', '명절 배송 일정 안내', '설 연휴 기간 동안 일부 상품 배송이 지연될 수 있습니다. 너그러운 양해 부탁드립니다.'),
       ('52ac9dd8-ea92-45a1-8b5e-0cfae0c6c6f9', '긴급 점검 공지', '서버 이슈로 인해 긴급 점검이 진행 중입니다. 빠르게 복구하겠습니다.'),
       ('0c0113db-4ef2-4609-9ea0-06d6fa6d7b0f', '회원 등급제 도입', '활동량에 따라 등급이 나뉘는 회원 등급제가 도입되었습니다. 다양한 혜택을 누려보세요.'),
       ('e3e1f6b1-6355-4296-b6f1-d156cb71c4e2', '커뮤니티 이용 가이드', '건강한 커뮤니티를 위해 이용 가이드를 확인해주세요. 위반 시 제재될 수 있습니다.');

-- app_version
INSERT INTO app_version (version, force_update, download_url, released_at)
VALUES ('1.0.0', 0, 'https://example.com/download/v1.0.0', NOW()),
       ('1.0.1', 0, 'https://example.com/download/v1.0.1', NOW()),
       ('1.1.0', 1, 'https://example.com/download/v1.1.0', NOW()),
       ('1.2.0', 0, 'https://example.com/download/v1.2.0', NOW()),
       ('1.2.1', 0, 'https://example.com/download/v1.2.1', NOW()),
       ('1.3.0', 1, 'https://example.com/download/v1.3.0', NOW()),
       ('1.4.0', 0, 'https://example.com/download/v1.4.0', NOW()),
       ('1.4.1', 0, 'https://example.com/download/v1.4.1', NOW()),
       ('1.5.0', 1, 'https://example.com/download/v1.5.0', NOW()),
       ('2.0.0', 1, 'https://example.com/download/v2.0.0', NOW());
