-- user 테이블 더미 데이터 삽입
INSERT INTO user (email, nickname, birth_date, bio, participation, img_url, thumb_img_url, notified, last_login_at,
                  updated_at, auth_provider, status, role)
VALUES ('alice@example.com', '김민지', '1995-06-15', '여행과 독서를 좋아합니다.', 5, 'https://example.com/img1.jpg',
        'https://example.com/thumb1.jpg', 1, NOW(), NOW(), 1, 0, 0),
       ('bob@example.com', '이준호', '1998-02-20', '등산을 즐깁니다.', 3, 'https://example.com/img2.jpg',
        'https://example.com/thumb2.jpg', 1, NOW(), NOW(), 2, 0, 1),
       ('charlie@example.com', '박서연', '2000-11-11', '사진 촬영이 취미입니다.', 8, 'https://example.com/img3.jpg',
        'https://example.com/thumb3.jpg', 1, NOW(), NOW(), 1, 0, 0),
       ('dave@example.com', '최민수', '1997-04-05', '도시 탐방을 즐깁니다.', 6, 'https://example.com/img4.jpg',
        'https://example.com/thumb4.jpg', 1, NOW(), NOW(), 1, 0, 1),
       ('eva@example.com', '정다은', '1993-09-23', '요가와 명상을 좋아합니다.', 2, 'https://example.com/img5.jpg',
        'https://example.com/thumb5.jpg', 1, NOW(), NOW(), 2, 0, 0),
       ('frank@example.com', '한지훈', '1995-12-30', '해외여행을 자주 다닙니다.', 4, 'https://example.com/img6.jpg',
        'https://example.com/thumb6.jpg', 1, NOW(), NOW(), 1, 0, 1),
       ('grace@example.com', '김수현', '2001-05-21', '캠핑과 하이킹을 좋아합니다.', 9, 'https://example.com/img7.jpg',
        'https://example.com/thumb7.jpg', 1, NOW(), NOW(), 2, 0, 0),
       ('henry@example.com', '이서준', '1999-07-14', '배낭여행을 준비 중!', 7, 'https://example.com/img8.jpg',
        'https://example.com/thumb8.jpg', 1, NOW(), NOW(), 1, 0, 1),
       ('isabel@example.com', '박하늘', '1996-10-02', '도전하는 삶!', 5, 'https://example.com/img9.jpg',
        'https://example.com/thumb9.jpg', 1, NOW(), NOW(), 2, 0, 0),
       ('jack@example.com', '최예린', '1994-08-08', '세상을 탐험 중', 1, 'https://example.com/img10.jpg',
        'https://example.com/thumb10.jpg', 1, NOW(), NOW(), 1, 0, 1);

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

-- user_preference 테이블 더미 데이터 삽입
INSERT INTO user_preference (user_id, preference_id)
VALUES (1, 2),
       (1, 6),
       (1, 15), -- 김민지: F, PRACTICAL, FOOD
       (2, 1),
       (2, 10),
       (2, 18), -- 이준호: M, EXTROVERTED, ADVENTURE
       (3, 2),
       (3, 7),
       (3, 14), -- 박서연: F, SOCIAL, ART
       (4, 1),
       (4, 8),
       (4, 20), -- 최민수: M, OPTIMISTIC, SHOP
       (5, 2),
       (5, 9),
       (5, 17), -- 정다은: F, FREE, NATURE
       (6, 1),
       (6, 6),
       (6, 16), -- 한지훈: M, INTROVERTED, URBAN
       (7, 2),
       (7, 11),
       (7, 19), -- 김수현: F, CAREFUL, RELAX
       (8, 1),
       (8, 12),
       (8, 13), -- 이서준: M, BOLD, ACTIVITY
       (9, 2),
       (9, 7),
       (9, 15), -- 박하늘: F, SOCIAL, FOOD
       (10, 1),
       (10, 9),
       (10, 14); -- 최예린: M, FREE, ART

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
