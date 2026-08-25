-- 康识问诊 建表脚本（MySQL 8 / H2 MODE=MySQL 通用）
-- 幂等：只用 CREATE TABLE IF NOT EXISTS，不含 ALTER 迁移语句。
-- 历史版本的 ALTER 已并入下方各表定义，升级旧库请重建（MySQL 删库重建 / 删除 backend/data 下的 H2 文件）。

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT,
    citations_json TEXT,
    attachments_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id BIGINT,
    message_id BIGINT,
    filename VARCHAR(255),
    mime_type VARCHAR(64),
    stored_name VARCHAR(128) NOT NULL,
    byte_size BIGINT,
    ocr_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 一个用户可建多份档案（本人 / 家人），故 user_id 上不加唯一约束
CREATE TABLE IF NOT EXISTS health_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(64),
    relation VARCHAR(32),
    age INT,
    sex VARCHAR(16),
    height_cm DOUBLE,
    weight_kg DOUBLE,
    allergies VARCHAR(500),
    shared_to_admin BOOLEAN NOT NULL DEFAULT FALSE,
    last_advice TEXT,
    advice_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS health_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT,
    metric_type VARCHAR(64) NOT NULL,
    metric_value DOUBLE NOT NULL,
    unit VARCHAR(32),
    recorded_at TIMESTAMP NOT NULL,
    note VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS health_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT,
    disease VARCHAR(128) NOT NULL,
    diagnosed_at DATE,
    status VARCHAR(32),
    note VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS exam_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT,
    filename VARCHAR(255),
    raw_text TEXT,
    summary TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam_report_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    item_value VARCHAR(64),
    unit VARCHAR(32),
    ref_range VARCHAR(64),
    flag VARCHAR(16),
    interpretation TEXT
);

CREATE TABLE IF NOT EXISTS kb_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(64),
    source VARCHAR(512),
    source_url VARCHAR(512),
    publisher VARCHAR(128),
    filename VARCHAR(255),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    ordinal INT NOT NULL
);
