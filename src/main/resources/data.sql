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

INSERT INTO plan (uuid, itin_start, itin_finish, location, center_lat, center_lng, memo, user_id)
VALUES ('a4eec2ab-072c-4555-a00e-c0059cd2407a', NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), '서울', 37.5665, 126.9780,
        '서울 여행 계획 1', 1),
       ('1327959b-174e-4a29-81d6-0818aa3c1c96', NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), '서울', 37.5651, 126.9895,
        '서울 여행 계획 2', 2),
       ('c77787c3-5b97-4028-9930-004a00c46d09', NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), '유럽', 48.8566, 2.3522,
        '유럽 여행 계획', 3),
       ('b52484c1-5c34-4b58-9f04-3f39006ae295', NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), '전국', 36.5, 127.5, '국내 여행 계획',
        4),
       ('6498d2ec-a444-4afc-ab08-ac11db5833c0', NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), '부산', 35.1796, 129.0756,
        '부산 여행 계획', 5),
       ('a101b53e-c88f-468c-8f49-4c3eefee0efb', NOW(), DATE_ADD(NOW(), INTERVAL 10 DAY), '프랑스', 48.864716, 2.349014,
        '프랑스 여행 계획', 6),
       ('9c0dba24-d2b8-43a5-92e6-6c2a05a037b9', NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), '제주도', 33.4996, 126.5312,
        '제주도 여행 계획', 7),
       ('87e8b8c9-f194-430c-a0d0-23d6bb088ce5', NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), '전국', 37.5, 127.0, '사진 여행 계획',
        8),
       ('06b3d5ef-d5cb-43ca-bc90-e7429687bf21', NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), '일본', 35.6895, 139.6917,
        '일본 여행 계획 1', 9),
       ('c0ada66c-4242-4629-b078-aeaa4af8d715', NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), '일본', 34.6937, 135.5023,
        '일본 여행 계획 2', 10);


INSERT INTO post (uuid, title, content, views, capacity, joined, itin_start, itin_finish, location, created_at,
                  last_edited_at,
                  post_status, author_id, party_id, plan_id)
VALUES ('0f6e967f-2608-46aa-89e9-b867dcfb2a87', '첫 번째 여행 모집', '함께 여행 가실 분 모집합니다!', 23, 3, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 3 DAY), '서울', NOW(), NOW(), 1, 1, NULL, NULL),
       ('7dc58749-aa1b-468b-b811-1d1afa644da5', '서울 맛집 탐방', '서울에서 가장 맛있는 음식점을 공유해요.', 45, 2, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 2 DAY), '서울', NOW(), NOW(), 1, 2, NULL, NULL),
       ('b32416ea-2e3c-40eb-addf-0145dac51762', '배낭여행 정보 공유', '배낭여행 팁과 노하우를 공유합니다.', 67, 5, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 5 DAY), '유럽', NOW(), NOW(), 1, 3, NULL, NULL),
       ('cbb9a7e6-87da-4272-b85b-6323aaac2e5f', '혼자 여행 꿀팁', '혼자 여행할 때 유용한 정보를 제공합니다.', 12, 1, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 1 DAY), '전국', NOW(), NOW(), 1, 4, NULL, 4),
       ('0f682e11-ad70-43f8-b258-9ddb5b8f8654', '새로운 여행 동반자 구함', '여행을 함께할 친구를 찾아요.', 34, 4, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 2 DAY), '부산', NOW(), NOW(), 1, 5, NULL, 5),
       ('403e6193-e564-4700-8de9-dde08f5abef1', '유럽 여행 후기', '유럽 여행에서 가장 좋았던 경험을 공유합니다.', 89, 6, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 10 DAY), '프랑스', NOW(), NOW(), 1, 6, NULL, 6),
       ('ec87d3d8-e2f3-4e2e-83f3-145ddf49c922', '국내 여행 추천', '국내에서 꼭 가봐야 할 곳을 추천합니다.', 78, 3, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 3 DAY), '제주도', NOW(), NOW(), 1, 7, NULL, 7),
       ('14346672-7785-43f5-9d0c-9f946ea0c7b0', '여행 사진 공유', '여행 중 찍은 사진을 공유해요.', 56, 2, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 2 DAY), '전국', NOW(), NOW(), 1, 8, NULL, 8),
       ('0e171266-4ff0-40f9-a9f5-5c4ceaccff41', '여행 비용 절약 팁', '여행 경비를 아끼는 다양한 방법!', 34, 1, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 1 DAY), '일본', NOW(), NOW(), 1, 9, NULL, 9),
       ('6162ea57-899d-4e3e-b8db-552d575909b2', '일본 여행 계획', '일본 여행 일정과 계획을 공유합니다.', 22, 3, 1, NOW(),
        DATE_ADD(NOW(), INTERVAL 4 DAY), '일본', NOW(), NOW(), 1, 10, NULL, 10);


INSERT INTO party (uuid, itin_start, itin_finish, location, joined, capacity, name, created_at,
                   last_edited_at, plan_id)
VALUES ('88d98b41-c34b-42ea-9f0f-07440e02478f', NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), '서울', 1, 3, '서울모임1', NOW(),
        NOW(), 1),
       ('f86abba4-3fbd-43d3-b3d3-48932cfa4ef7', NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), '서울', 1, 2, '서울모임2', NOW(),
        NOW(), 2),
       ('3f56afea-d0cd-4f95-abad-188654680349', NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), '유럽', 1, 5, '유럽모임', NOW(),
        NOW(), 3),
       ('916780cc-abc1-40d2-8b2d-cb8feb1ca171', NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), '전국', 1, 1, '국내모임', NOW(),
        NOW(), 4),
       ('570dc0df-ba7b-448b-96c4-b73df5fe53ec', NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), '부산', 1, 4, '부산모임', NOW(),
        NOW(), 5),
       ('2b6b7cbc-1e77-42ea-845d-f2c70423d43d', NOW(), DATE_ADD(NOW(), INTERVAL 10 DAY), '프랑스', 1, 6, '프랑스모임', NOW(),
        NOW(), 6),
       ('b7df7b84-fabb-4875-b167-e3b5af23e919', NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), '제주도', 1, 3, '제주모임', NOW(),
        NOW(), 7),
       ('26d16d83-13ca-495c-aac4-9a84bdcd068a', NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), '전국', 1, 2, '사진모임', NOW(),
        NOW(), 8),
       ('c7e98d3b-b468-498c-a02a-c9dee2c5de7a', NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), '일본', 1, 1, '일본모임1', NOW(),
        NOW(), 9),
       ('0b12d1db-80ae-4ad3-8f66-670f7cb95c5d', NOW(), DATE_ADD(NOW(), INTERVAL 4 DAY), '일본', 1, 3, '일본모임2', NOW(),
        NOW(), 10);


-- marker 더미 데이터
INSERT INTO marker (uuid, lat, lng, date, idx, place, memo, plan_id)
VALUES ('127c6fc0-3ecf-441c-b92d-33a96c92f924', 37.5760, 126.9769, NOW(), 1, '경복궁', '경복궁 투어', 1),
       ('5a5f6e18-7a2d-4e18-8a8b-55b3ec6a9a1b', 37.5704, 126.9920, DATE_ADD(NOW(), INTERVAL 1 DAY), 2, '인사동',
        '전통 거리 탐방', 1),
       ('faec0b26-cb3b-4e8e-bbb6-4e7469d8593e', 35.1587, 129.1603, NOW(), 1, '해운대 해수욕장', '바다 산책', 2),
       ('4ccbbec2-8c2b-4f62-a647-9e5b78557e94', 35.1796, 129.0756, DATE_ADD(NOW(), INTERVAL 1 DAY), 2, '광안리', '야경 감상',
        2),
       ('3b4cc8af-fb86-4d37-9ee7-6de2cd0e9f53', 33.4507, 126.5707, NOW(), 1, '한라산', '등반 일정', 3),
       ('ad8c6f87-0b9b-4d55-9e87-f5be69ccda67', 33.4996, 126.5312, DATE_ADD(NOW(), INTERVAL 2 DAY), 2, '카페거리',
        '감성 카페 투어', 3);

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

INSERT INTO marker (uuid, lat, lng, date, idx, place, memo, plan_id)
VALUES ('c0516325-df78-44d8-b17a-78f9d2a83502', 37.571, 126.976, NOW(), 1, '광화문', '서울 도심 산책', 1),
       ('c44ad795-59d5-4805-8b2b-b3d162b4f947', 37.573, 126.978, NOW(), 2, '북촌한옥마을', '한옥 감상', 1),
       ('d3f44f47-f5fa-41ba-85d6-550b8b3523c6', 37.576, 126.985, NOW(), 3, '청계천', '산책로', 1),

       ('33414c72-b9e8-43fd-87f6-6046929d55c3', 37.565, 126.990, NOW(), 1, '남산타워', '야경', 2),
       ('b9035109-24da-4c71-b026-e6a22a9fbe08', 37.561, 126.995, NOW(), 2, '명동거리', '쇼핑', 2),
       ('588df9fb-3aa4-45eb-842a-2d3f4201e6fc', 37.560, 126.980, NOW(), 3, '서울역', '기차여행', 2),

       ('28b2edb7-bf7b-48ae-81f6-3786c65464b0', 48.8566, 2.3522, NOW(), 1, '에펠탑', '파리 명소', 3),
       ('7cd31fc4-1dcd-4c96-bf2c-605f71ec8343', 48.8606, 2.3376, NOW(), 2, '루브르', '미술관 관람', 3),
       ('ae0d6654-e81f-462d-80cb-dd3dbb02ff10', 48.8738, 2.2950, NOW(), 3, '개선문', '야경 촬영', 3),

       ('3be06d57-37b5-454f-8b0c-c661c1c70668', 36.3504, 127.3845, NOW(), 1, '대전역', '전국 투어 출발', 4),
       ('3413c6df-aa2e-40f8-857b-eb0508c8732b', 35.1796, 129.0756, NOW(), 2, '부산역', '환승지점', 4),
       ('cb1a8507-7926-4a7b-854a-530675fe9a7b', 35.8714, 128.6014, NOW(), 3, '대구시내', '도시 탐방', 4),

       ('dfb95b09-8a35-4fdf-8c7e-bcabf4cb6ca7', 35.1587, 129.1603, NOW(), 1, '해운대', '해변 산책', 5),
       ('8ee5ac3f-5a3e-4e66-9bdf-3fcb30e24e77', 35.1534, 129.1189, NOW(), 2, '광안리', '야경 촬영', 5),
       ('527601a2-4ca2-4c5d-a036-5f0b7acb1240', 35.1796, 129.0756, NOW(), 3, '부산타워', '정상 뷰 감상', 5),

       ('43abe8bf-2b12-4b0c-9d8e-3d1ea6eec62c', 48.853, 2.349, NOW(), 1, '샹젤리제', '쇼핑 거리', 6),
       ('99716a35-cb2c-4777-acaa-2a94681c9667', 48.855, 2.336, NOW(), 2, '오르세 미술관', '인상파 화풍 감상', 6),
       ('e8a76f33-4e63-47dc-8ae7-95ba12716a88', 48.857, 2.295, NOW(), 3, '몽마르뜨', '언덕 위 산책', 6),

       ('9042e8d3-66bb-408a-8071-91ba6d9f2cf0', 33.4507, 126.5707, NOW(), 1, '한라산', '등산', 7),
       ('6ca4dd87-2664-4c61-860a-b37d9d71b5f9', 33.4996, 126.5312, NOW(), 2, '제주시청', '중심 방문', 7),
       ('a1a6a31e-05ad-4768-ba0f-43e727877437', 33.5123, 126.5235, NOW(), 3, '이호테우', '해변 감상', 7),

       ('c527454c-c66a-417c-b1a5-08b0610f39ce', 37.5665, 127.0, NOW(), 1, '서울숲', '감성 사진', 8),
       ('d1288d51-3b60-4375-a748-751364e855e9', 37.5600, 126.9750, NOW(), 2, 'DDP', '야간 사진', 8),
       ('50723040-3956-4ad9-9fe5-bbccaba0aced', 37.5721, 126.9831, NOW(), 3, '창덕궁', '역사적 장소', 8),

       ('e9727897-a8d2-404d-9352-d28e75f7d0a9', 35.6586, 139.7454, NOW(), 1, '도쿄타워', '야경', 9),
       ('b8926bbf-b333-4cc7-8a8c-3f688e1a1143', 35.6764, 139.6993, NOW(), 2, '신주쿠', '쇼핑', 9),
       ('0e38511d-cde3-4795-946c-8c6506edf322', 35.6895, 139.6917, NOW(), 3, '시부야', '도보 여행', 9),

       ('b9385315-362c-4558-aea3-3c9b0f2dee0f', 34.6937, 135.5023, NOW(), 1, '오사카성', '관광 명소', 10),
       ('21c47c64-296b-4e4f-a235-a7566d75526f', 34.7025, 135.4959, NOW(), 2, '도톤보리', '야시장', 10),
       ('cb0fd411-e393-4281-be06-af1b62ae3ff7', 34.6937, 135.5023, NOW(), 3, '우메다', '전망대 감상', 10);

INSERT INTO user_party (role, party_id, user_id)
VALUES ('LEADER', 1, 1),
       ('MEMBER', 1, 2),
       ('MEMBER', 1, 3),
       ('LEADER', 2, 2),
       ('MEMBER', 2, 3),
       ('MEMBER', 2, 4),
       ('LEADER', 3, 3),
       ('MEMBER', 3, 4),
       ('MEMBER', 3, 5),
       ('LEADER', 4, 4),
       ('MEMBER', 4, 5),
       ('MEMBER', 4, 6),
       ('LEADER', 5, 5),
       ('MEMBER', 5, 6),
       ('MEMBER', 5, 7),
       ('LEADER', 6, 6),
       ('MEMBER', 6, 7),
       ('MEMBER', 6, 8),
       ('LEADER', 7, 7),
       ('MEMBER', 7, 8),
       ('MEMBER', 7, 9),
       ('LEADER', 8, 8),
       ('MEMBER', 8, 9),
       ('MEMBER', 8, 10),
       ('LEADER', 9, 9),
       ('MEMBER', 9, 10),
       ('MEMBER', 9, 1),
       ('LEADER', 10, 10),
       ('MEMBER', 10, 1),
       ('MEMBER', 10, 2);
