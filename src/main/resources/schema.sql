SET foreign_key_checks = 0; # FK 체크를 무시함으로써 테이블을 바로 삭제할 수 있음
DROP TABLE IF EXISTS User;
DROP TABLE IF EXISTS Post;
DROP TABLE IF EXISTS Party;
DROP TABLE IF EXISTS ChatMessage;
DROP TABLE IF EXISTS Plan;
DROP TABLE IF EXISTS Marker;
DROP TABLE IF EXISTS Notification;
DROP TABLE IF EXISTS Notice;
DROP TABLE IF EXISTS Preference;
DROP TABLE IF EXISTS UserPreference;
DROP TABLE IF EXISTS CompPreference;
DROP TABLE IF EXISTS PartyApplication;
DROP TABLE IF EXISTS UserParty;
SET foreign_key_checks = 1; # FK 체크 재활성화


CREATE TABLE User
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(500) NOT NULL UNIQUE,
    nickname      VARCHAR(20)  NOT NULL UNIQUE,
    gender        TINYINT      NOT NULL,
    age           TINYINT      NOT NULL,
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

CREATE TABLE Post
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

CREATE TABLE Party
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

CREATE TABLE ChatMessage
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    body       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id    INT,
    plan_id    INT
);

CREATE TABLE Plan
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

CREATE TABLE Marker
(
    id      INT AUTO_INCREMENT PRIMARY KEY,
    lat     DECIMAL(13, 10) NOT NULL,
    lng     DECIMAL(13, 10) NOT NULL,
    time    TIMESTAMP       NOT NULL,
    place   VARCHAR(50),
    memo    VARCHAR(500),
    plan_id INT
);

CREATE TABLE Notification
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    type         TINYINT      NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message      VARCHAR(500) NOT NULL,
    reference_id INT          NOT NULL,
    isRead       TINYINT      NOT NULL DEFAULT 0,
    user_id      INT
);

CREATE TABLE Notice
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(50)   NOT NULL,
    body           VARCHAR(1000) NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Preference
(
    id   INT AUTO_INCREMENT PRIMARY KEY,
    type TINYINT     NOT NULL,
    name VARCHAR(20) NOT NULL
);

CREATE TABLE UserPreference
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT,
    preference_id INT
);

CREATE TABLE CompPreference
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    post_id       INT,
    preference_id INT
);

CREATE TABLE PartyApplication
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(50) NOT NULL,
    body       VARCHAR(500),
    status     TINYINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    party_id   INT,
    user_id    INT
);

CREATE TABLE UserParty
(
    id        INT AUTO_INCREMENT PRIMARY KEY,
    role      TINYINT   NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    party_id  INT,
    user_id   INT
);

ALTER TABLE Post
    ADD FOREIGN KEY (user_id) REFERENCES User (id);

ALTER TABLE Post
    ADD FOREIGN KEY (party_id) REFERENCES Party (id);

ALTER TABLE Post
    ADD FOREIGN KEY (plan_id) REFERENCES Plan (id);

ALTER TABLE Party
    ADD FOREIGN KEY (plan_id) REFERENCES Plan (id);

ALTER TABLE ChatMessage
    ADD FOREIGN KEY (user_id) REFERENCES User (id);

ALTER TABLE ChatMessage
    ADD FOREIGN KEY (plan_id) REFERENCES Plan (id);

ALTER TABLE Plan
    ADD FOREIGN KEY (user_id) REFERENCES User (id);

ALTER TABLE Marker
    ADD FOREIGN KEY (plan_id) REFERENCES Plan (id);

ALTER TABLE Notification
    ADD FOREIGN KEY (user_id) REFERENCES User (id);

ALTER TABLE UserPreference
    ADD FOREIGN KEY (user_id) REFERENCES User (id);

ALTER TABLE UserPreference
    ADD FOREIGN KEY (preference_id) REFERENCES Preference (id);

ALTER TABLE CompPreference
    ADD FOREIGN KEY (post_id) REFERENCES Post (id);

ALTER TABLE CompPreference
    ADD FOREIGN KEY (preference_id) REFERENCES Preference (id);

ALTER TABLE PartyApplication
    ADD FOREIGN KEY (party_id) REFERENCES Party (id);

ALTER TABLE PartyApplication
    ADD FOREIGN KEY (user_id) REFERENCES User (id);

ALTER TABLE UserParty
    ADD FOREIGN KEY (party_id) REFERENCES Party (id);

ALTER TABLE UserParty
    ADD FOREIGN KEY (user_id) REFERENCES User (id);
