-- 康识问诊 建表脚本（MySQL 8 / H2 MODE=MySQL 通用）
-- 幂等：只用 CREATE TABLE IF NOT EXISTS，不含 ALTER 迁移语句。
-- 索引写在表定义里（内联 KEY），MySQL 8 与 H2 MODE=MySQL 都接受；
-- 因此索引只对新建的库生效，旧库请按下方说明重建。
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 会话列表按 user_id 过滤、updated_at 倒序
    KEY idx_chat_session_user (user_id, updated_at)
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT,
    attachments_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 消息列表按 session_id 过滤、created_at 正序
    KEY idx_chat_message_session (session_id, created_at),
    KEY idx_chat_message_user (user_id)
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chat_image_session (session_id),
    KEY idx_chat_image_message (message_id),
    KEY idx_chat_image_user (user_id)
);

CREATE TABLE IF NOT EXISTS favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 同一条消息不应被同一用户收藏两次
    CONSTRAINT uk_favorite_user_message UNIQUE (user_id, message_id)
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_health_profile_user (user_id)
);

CREATE TABLE IF NOT EXISTS health_metric (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT,
    metric_type VARCHAR(64) NOT NULL,
    metric_value DOUBLE NOT NULL,
    unit VARCHAR(32),
    recorded_at TIMESTAMP NOT NULL,
    note VARCHAR(500),
    -- 趋势查询按档案 + 指标类型过滤，再按时间排序
    KEY idx_health_metric_profile (profile_id, metric_type, recorded_at),
    KEY idx_health_metric_user (user_id, recorded_at)
);

CREATE TABLE IF NOT EXISTS health_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT,
    disease VARCHAR(128) NOT NULL,
    diagnosed_at DATE,
    status VARCHAR(32),
    note VARCHAR(500),
    KEY idx_health_history_profile (profile_id),
    KEY idx_health_history_user (user_id)
);

CREATE TABLE IF NOT EXISTS exam_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT,
    filename VARCHAR(255),
    raw_text TEXT,
    summary TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_exam_report_user (user_id, created_at),
    KEY idx_exam_report_profile (profile_id)
);

CREATE TABLE IF NOT EXISTS exam_report_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    item_value VARCHAR(64),
    unit VARCHAR(32),
    ref_range VARCHAR(64),
    flag VARCHAR(16),
    interpretation TEXT,
    KEY idx_exam_report_item_report (report_id)
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_kb_document_category (category)
);

CREATE TABLE IF NOT EXISTS kb_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    ordinal INT NOT NULL,
    -- 重建向量库时按文档批量取块
    KEY idx_kb_chunk_document (document_id, ordinal)
);

-- 首页健康新闻：启动/定时从权威源爬取。source_url 去重在代码里做
-- （行数最多几十条，且 VARCHAR 前缀唯一索引在 H2 上不可用）。
CREATE TABLE IF NOT EXISTS news_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(512) NOT NULL,
    summary VARCHAR(1024),
    content MEDIUMTEXT,
    image_name VARCHAR(128),
    builtin_image VARCHAR(64),
    source_name VARCHAR(128),
    source_url VARCHAR(768),
    category VARCHAR(32),
    published_on DATE,
    crawled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_news_published (published_on, id)
);

-- 导诊「附近医疗资源」：用户主动勾选才保存的位置（一人一条，重复保存即覆盖）。
-- 坐标只在用户明示保存时落库；默认「用完即走」不落库。
CREATE TABLE IF NOT EXISTS user_location (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    address_text VARCHAR(255),
    longitude DOUBLE,
    latitude DOUBLE,
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_location_user UNIQUE (user_id)
);
