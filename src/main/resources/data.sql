INSERT INTO user (email, nickname, birth_date, bio, participation, img_url, thumb_img_url, notified, last_login_at,
                  updated_at, auth_provider, status, role)
VALUES ('alice@example.com', '김민지', '1995-06-15', '여행과 독서를 좋아합니다.', 5, 'https://example.com/img1.jpg',
        'https://example.com/thumb1.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'USER'),
       ('bob@example.com', '이준호', '1998-02-20', '', 3, 'https://example.com/img2.jpg',
        'https://example.com/thumb2.jpg', 1, NOW(), NOW(), 'GOOGLE', 'NORMAL', 'ADMIN'),
       ('charlie@example.com', '박서연', '2000-11-11', '사진 촬영이 취미입니다.', 8, 'https://example.com/img3.jpg',
        'https://example.com/thumb3.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'USER'),
       ('dave@example.com', '최민수', '1997-04-05', '도시 탐방을 즐깁니다.', 6, 'https://example.com/img4.jpg',
        'https://example.com/thumb4.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'ADMIN'),
       ('eva@example.com', '정다은', '1993-09-23', '요가와 명상을 좋아합니다.', 2, 'https://example.com/img5.jpg',
        'https://example.com/thumb5.jpg', 1, NOW(), NOW(), 'GOOGLE', 'NORMAL', 'USER'),
       ('frank@example.com', '한지훈', '1995-12-30', '', 4, 'https://example.com/img6.jpg',
        'https://example.com/thumb6.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'ADMIN'),
       ('grace@example.com', '김수현', '2001-05-21', '캠핑과 하이킹을 좋아합니다.', 9, 'https://example.com/img7.jpg',
        'https://example.com/thumb7.jpg', 1, NOW(), NOW(), 'GOOGLE', 'NORMAL', 'USER'),
       ('henry@example.com', '이서준', '1999-07-14', '배낭여행을 준비 중!', 7, 'https://example.com/img8.jpg',
        'https://example.com/thumb8.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'ADMIN'),
       ('isabel@example.com', '박하늘', '1996-10-02', '도전하는 삶!', 5, 'https://example.com/img9.jpg',
        'https://example.com/thumb9.jpg', 1, NOW(), NOW(), 'GOOGLE', 'NORMAL', 'USER'),
       ('jack@example.com', '최예린', '1994-08-08', '세상을 탐험 중', 1, 'https://example.com/img10.jpg',
        'https://example.com/thumb10.jpg', 1, NOW(), NOW(), 'KAKAO', 'NORMAL', 'ADMIN');

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

-- post 테이블 더미 데이터 삽입
INSERT INTO post (title, body, views, created_at, last_edited_at, post_status, author_id, party_id, plan_id)
VALUES ('첫 번째 여행 모집', '함께 여행 가실 분 모집합니다!', 23, NOW(), NOW(), 1, 1, NULL, NULL),
       ('서울 맛집 탐방', '서울에서 가장 맛있는 음식점을 공유해요.', 45, NOW(), NOW(), 1, 2, NULL, NULL),
       ('배낭여행 정보 공유', '배낭여행 팁과 노하우를 공유합니다.', 67, NOW(), NOW(), 1, 3, NULL, NULL),
       ('혼자 여행 꿀팁', '혼자 여행할 때 유용한 정보를 제공합니다.', 12, NOW(), NOW(), 1, 4, NULL, NULL),
       ('새로운 여행 동반자 구함', '여행을 함께할 친구를 찾아요.', 34, NOW(), NOW(), 1, 5, NULL, NULL),
       ('유럽 여행 후기', '유럽 여행에서 가장 좋았던 경험을 공유합니다.', 89, NOW(), NOW(), 1, 6, NULL, NULL),
       ('국내 여행 추천', '국내에서 꼭 가봐야 할 곳을 추천합니다.', 78, NOW(), NOW(), 1, 7, NULL, NULL),
       ('여행 사진 공유', '여행 중 찍은 사진을 공유해요.', 56, NOW(), NOW(), 1, 8, NULL, NULL),
       ('여행 비용 절약 팁', '여행 경비를 아끼는 다양한 방법!', 34, NOW(), NOW(), 1, 9, NULL, NULL),
       ('일본 여행 계획', '일본 여행 일정과 계획을 공유합니다.', 22, NOW(), NOW(), 1, 10, NULL, NULL);

-- app_version 테이블 더미 데이터 삽입
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
