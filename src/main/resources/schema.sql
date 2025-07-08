SET foreign_key_checks = 0;
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
DROP TABLE IF EXISTS app_version;
SET foreign_key_checks = 1;

CREATE TABLE user
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    uuid          CHAR(36)     NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    nickname      VARCHAR(10)  NOT NULL UNIQUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    birth_date    DATE         NOT NULL,
    bio           VARCHAR(50),
    participation INT          NOT NULL DEFAULT 0,
    img_url       VARCHAR(500),
    thumb_img_url VARCHAR(500),
    notified      TINYINT(1)   NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    auth_provider VARCHAR(10)  NOT NULL,
    status        VARCHAR(10)           DEFAULT 'NORMAL',
    role          VARCHAR(10)  NOT NULL
);

CREATE TABLE post
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    uuid           CHAR(36)     NOT NULL UNIQUE,
    title          VARCHAR(50)  NOT NULL,
    content        VARCHAR(500) NOT NULL,
    is_instant     TINYINT(1)   NOT NULL DEFAULT 0,
    views          INT          NOT NULL DEFAULT 0,
    capacity       INT          NOT NULL DEFAULT 1,
    joined         INT          NOT NULL DEFAULT 1,
    itin_start     TIMESTAMP    NOT NULL,
    itin_finish    TIMESTAMP    NOT NULL,
    location       VARCHAR(50),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    post_status    TINYINT      NOT NULL DEFAULT 1,
    author_id      INT          NOT NULL,
    party_id       INT,
    plan_id        INT
);

CREATE TABLE party
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    uuid           CHAR(36)    NOT NULL UNIQUE,
    itin_start     TIMESTAMP   NOT NULL,
    itin_finish    TIMESTAMP   NOT NULL,
    location       VARCHAR(50) NOT NULL,
    joined         TINYINT     NOT NULL,
    capacity       TINYINT     NOT NULL,
    name           VARCHAR(50) NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    plan_id        INT         NOT NULL
);

CREATE TABLE chat_message
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    body       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id    INT           NOT NULL,
    plan_id    INT           NOT NULL
);

CREATE TABLE plan
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    uuid        CHAR(36)        NOT NULL UNIQUE,
    itin_start  TIMESTAMP       NOT NULL,
    itin_finish TIMESTAMP       NOT NULL,
    location    VARCHAR(50)     NOT NULL,
    center_lat  DECIMAL(13, 10) NOT NULL,
    center_lng  DECIMAL(13, 10) NOT NULL,
    memo        VARCHAR(500),
    owner       INT             NOT NULL
);

CREATE TABLE marker
(
    id      INT AUTO_INCREMENT PRIMARY KEY,
    uuid    CHAR(36)        NOT NULL UNIQUE,
    lat     DECIMAL(13, 10) NOT NULL,
    lng     DECIMAL(13, 10) NOT NULL,
    date    TIMESTAMP       NOT NULL,
    idx     INT             NOT NULL,
    place   VARCHAR(50),
    memo    VARCHAR(500),
    plan_id INT             NOT NULL
);

CREATE TABLE notification
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    uuid         CHAR(36)     NOT NULL UNIQUE,
    type         TINYINT      NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message      VARCHAR(500) NOT NULL,
    reference_id INT          NOT NULL,
    isRead       TINYINT(1)   NOT NULL DEFAULT 0,
    user_id      INT          NOT NULL
);

CREATE TABLE notice
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    uuid           CHAR(36)      NOT NULL UNIQUE,
    title          VARCHAR(50)   NOT NULL,
    body           VARCHAR(1000) NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE preference
(
    id   INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    name VARCHAR(20) NOT NULL
);

CREATE TABLE user_preference
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT NOT NULL,
    preference_id INT NOT NULL
);

CREATE TABLE comp_preference
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    post_id       INT NOT NULL,
    preference_id INT NOT NULL
);

CREATE TABLE party_application
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(50) NOT NULL,
    body       VARCHAR(500),
    status     TINYINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    party_id   INT         NOT NULL,
    user_id    INT         NOT NULL
);

CREATE TABLE user_party
(
    id        INT AUTO_INCREMENT PRIMARY KEY,
    role      CHAR(10)  NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    party_id  INT       NOT NULL,
    user_id   INT       NOT NULL
);

CREATE TABLE app_version
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    version      VARCHAR(20)  NOT NULL UNIQUE,
    force_update TINYINT(1)   NOT NULL DEFAULT 0,
    download_url VARCHAR(500) NOT NULL,
    released_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Foreign key 설정은 동일하게 유지
ALTER TABLE post
    ADD FOREIGN KEY (author_id) REFERENCES user (id);
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
    ADD FOREIGN KEY (owner) REFERENCES user (id);
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
