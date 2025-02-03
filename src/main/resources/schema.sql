SET foreign_key_checks = 0; # FK 체크를 무시함으로써 테이블을 바로 삭제할 수 있음
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS party;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS plan;
DROP TABLE IF EXISTS marker;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS notice;
DROP TABLE IF EXISTS preference;
DROP TABLE IF EXISTS user_preference;
DROP TABLE IF EXISTS comp_preference;
DROP TABLE IF EXISTS party_application;
DROP TABLE IF EXISTS user_party;
SET foreign_key_checks = 1; # FK 체크 재활성화


CREATE TABLE user
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(500) NOT NULL UNIQUE,
    nickname      VARCHAR(20)  NOT NULL UNIQUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    bio           VARCHAR(500),
    img_url       VARCHAR(500),
    thumb_img_url VARCHAR(500),
    notified      TINYINT(1)   NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    auth_provider TINYINT      NOT NULL,
    status        TINYINT               DEFAULT 0,
    role          TINYINT               DEFAULT 0
);

CREATE TABLE post
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(50)  NOT NULL,
    body           VARCHAR(500) NOT NULL,
    preview        VARCHAR(100) NOT NULL,
    views          INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    post_status    TINYINT      NOT NULL DEFAULT 1,
    user_id        INT,
    party_id       INT,
    plan_id        INT
);

CREATE TABLE party
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    itin_start     TIMESTAMP   NOT NULL,
    itin_finish    TIMESTAMP   NOT NULL,
    destination    VARCHAR(50) NOT NULL,
    joined         TINYINT     NOT NULL,
    max            TINYINT     NOT NULL,
    name           VARCHAR(50) NOT NULL,
    img_url        VARCHAR(50) NOT NULL,
    thumb_img_url  VARCHAR(50) NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    plan_id        INT
);

CREATE TABLE chat_message
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    body       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id    INT,
    plan_id    INT
);

CREATE TABLE plan
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    itin_start  TIMESTAMP       NOT NULL,
    itin_finish TIMESTAMP       NOT NULL,
    destination VARCHAR(50)     NOT NULL,
    center_lat  DECIMAL(13, 10) NOT NULL,
    center_lng  DECIMAL(13, 10) NOT NULL,
    memo        VARCHAR(300),
    user_id     INT
);

CREATE TABLE marker
(
    id      INT AUTO_INCREMENT PRIMARY KEY,
    lat     DECIMAL(13, 10) NOT NULL,
    lng     DECIMAL(13, 10) NOT NULL,
    time    TIMESTAMP       NOT NULL,
    place   VARCHAR(50),
    memo    VARCHAR(500),
    plan_id INT
);

CREATE TABLE notification
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    type         TINYINT      NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message      VARCHAR(500) NOT NULL,
    reference_id INT          NOT NULL,
    isRead       TINYINT      NOT NULL DEFAULT 0,
    user_id      INT
);

CREATE TABLE notice
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(50)   NOT NULL,
    body           VARCHAR(1000) NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE preference
(
    id   INT AUTO_INCREMENT PRIMARY KEY,
    type TINYINT     NOT NULL,
    name VARCHAR(20) NOT NULL
);

CREATE TABLE user_preference
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT,
    preference_id INT
);

CREATE TABLE comp_preference
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    post_id       INT,
    preference_id INT
);

CREATE TABLE party_application
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(50) NOT NULL,
    body       VARCHAR(500),
    status     TINYINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    party_id   INT,
    user_id    INT
);

CREATE TABLE user_party
(
    id        INT AUTO_INCREMENT PRIMARY KEY,
    role      TINYINT   NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    party_id  INT,
    user_id   INT
);

ALTER TABLE post
    ADD FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE post
    ADD FOREIGN KEY (party_id) REFERENCES party (id);

ALTER TABLE post
    ADD FOREIGN KEY (plan_id) REFERENCES plan (id);

ALTER TABLE party
    ADD FOREIGN KEY (plan_id) REFERENCES plan (id);

ALTER TABLE chat_message
    ADD FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE chat_message
    ADD FOREIGN KEY (plan_id) REFERENCES plan (id);

ALTER TABLE plan
    ADD FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE marker
    ADD FOREIGN KEY (plan_id) REFERENCES plan (id);

ALTER TABLE notification
    ADD FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE user_preference
    ADD FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE;

ALTER TABLE user_preference
    ADD FOREIGN KEY (preference_id) REFERENCES preference (id);

ALTER TABLE comp_preference
    ADD FOREIGN KEY (post_id) REFERENCES post (id);

ALTER TABLE comp_preference
    ADD FOREIGN KEY (preference_id) REFERENCES preference (id);

ALTER TABLE party_application
    ADD FOREIGN KEY (party_id) REFERENCES party (id);

ALTER TABLE party_application
    ADD FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE user_party
    ADD FOREIGN KEY (party_id) REFERENCES party (id);

ALTER TABLE user_party
    ADD FOREIGN KEY (user_id) REFERENCES user (id);
