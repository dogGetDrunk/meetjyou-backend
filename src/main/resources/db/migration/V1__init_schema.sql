CREATE TABLE user
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    uuid          CHAR(36)     NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    nickname      VARCHAR(10)  NOT NULL UNIQUE,
    auth_provider VARCHAR(10)  NOT NULL,
    external_id   VARCHAR(100) NOT NULL UNIQUE,
    role          VARCHAR(10)  NOT NULL DEFAULT 'USER',
    bio           VARCHAR(50),
    participation INT          NOT NULL DEFAULT 0,
    status        VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    img_url       VARCHAR(500),
    thumb_img_url VARCHAR(500),
    notified      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    status         VARCHAR(32)  NOT NULL DEFAULT 'RECRUITING',
    is_plan_public BOOLEAN      NULL,
    author_id      INT          NOT NULL,
    party_id       INT,
    plan_id        INT
);

CREATE TABLE party
(
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    uuid               CHAR(36)    NOT NULL UNIQUE,
    itin_start         TIMESTAMP   NOT NULL,
    itin_finish        TIMESTAMP   NOT NULL,
    destination        VARCHAR(50) NOT NULL,
    joined             INT         NOT NULL,
    capacity           INT         NOT NULL,
    name               VARCHAR(50) NOT NULL,
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    progress_status    VARCHAR(30) NOT NULL DEFAULT 'PLANNING',
    recruitment_status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    plan_id            INT         NULL
);

CREATE TABLE chat_room
(
    room_id INT PRIMARY KEY,
    uuid    CHAR(36) NOT NULL UNIQUE
);

CREATE TABLE chat_message
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid       CHAR(36)      NOT NULL UNIQUE,
    room_id    INT           NOT NULL,
    sender_id  INT           NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_participant
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT       NOT NULL,
    room_id      INT       NOT NULL,
    last_read_at TIMESTAMP NULL DEFAULT NULL,

    UNIQUE (user_id, room_id)
);

CREATE TABLE plan
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    uuid        CHAR(36)        NOT NULL UNIQUE,
    itin_start  TIMESTAMP       NOT NULL,
    itin_finish TIMESTAMP       NOT NULL,
    destination VARCHAR(50)     NOT NULL,
    center_lat  DECIMAL(13, 10) NOT NULL,
    center_lng  DECIMAL(13, 10) NOT NULL,
    memo        VARCHAR(500),
    owner_id    INT             NOT NULL
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

CREATE TABLE terms
(
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    uuid               CHAR(36)     NOT NULL UNIQUE,
    type               VARCHAR(30)  NOT NULL,
    version            VARCHAR(20)  NOT NULL,
    display_text       VARCHAR(255) NOT NULL,
    required           BOOLEAN      NOT NULL,
    content_object_key VARCHAR(255) NOT NULL,
    content_hash       CHAR(64)     NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    effective_at       TIMESTAMP    NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_terms_type_version (type, version)
);

CREATE TABLE user_party
(
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    role                 CHAR(10)    NOT NULL,
    joined_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    member_status        VARCHAR(20) NOT NULL DEFAULT 'JOINED',
    last_read_message_id BIGINT      NULL,
    party_id             INT         NOT NULL,
    user_id              INT         NOT NULL
);

CREATE TABLE user_terms
(
    id        INT AUTO_INCREMENT PRIMARY KEY,
    user_id   INT       NOT NULL,
    terms_id  INT       NOT NULL,
    agreed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_terms_user_id_terms_id (user_id, terms_id)
);

CREATE TABLE app_version
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    version      VARCHAR(20)  NOT NULL UNIQUE,
    force_update TINYINT(1)   NOT NULL DEFAULT 0,
    download_url VARCHAR(500) NOT NULL,
    released_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE push_token
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid            CHAR(36)     NOT NULL UNIQUE,
    token           VARCHAR(255) NOT NULL UNIQUE,
    platform        VARCHAR(32)  NOT NULL,
    device_model    VARCHAR(64),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_updated_at TIMESTAMP    NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id         INT          NOT NULL,
    app_version_id  INT          NOT NULL
);

CREATE TABLE notification_outbox
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid         CHAR(36)     NOT NULL UNIQUE,
    type         VARCHAR(64)  NOT NULL,
    title        TEXT         NULL,
    body         TEXT         NULL,
    data_json    JSON         NOT NULL,
    dedup_key    VARCHAR(100) NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    attempts     INT          NOT NULL DEFAULT 0,
    available_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id      INT          NOT NULL
);

ALTER TABLE post
    ADD FOREIGN KEY (author_id) REFERENCES user (id);
ALTER TABLE post
    ADD FOREIGN KEY (party_id) REFERENCES party (id);
ALTER TABLE post
    ADD FOREIGN KEY (plan_id) REFERENCES plan (id);
ALTER TABLE party
    ADD FOREIGN KEY (plan_id) REFERENCES plan (id);
ALTER TABLE chat_room
    ADD FOREIGN KEY (room_id) REFERENCES party (id);
ALTER TABLE chat_message
    ADD FOREIGN KEY (room_id) REFERENCES chat_room (room_id);
ALTER TABLE chat_message
    ADD FOREIGN KEY (sender_id) REFERENCES user (id);
ALTER TABLE chat_participant
    ADD FOREIGN KEY (user_id) REFERENCES user (id);
ALTER TABLE chat_participant
    ADD FOREIGN KEY (room_id) REFERENCES chat_room (room_id);
ALTER TABLE plan
    ADD FOREIGN KEY (owner_id) REFERENCES user (id);
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
ALTER TABLE user_terms
    ADD FOREIGN KEY (user_id) REFERENCES user (id);
ALTER TABLE user_terms
    ADD FOREIGN KEY (terms_id) REFERENCES terms (id);
ALTER TABLE push_token
    ADD FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE;
ALTER TABLE push_token
    ADD FOREIGN KEY (app_version_id) REFERENCES app_version (id) ON DELETE CASCADE;
ALTER TABLE notification_outbox
    ADD FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE;
