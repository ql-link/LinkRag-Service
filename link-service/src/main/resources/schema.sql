-- ===============================================
-- toLink-Service 数据库初始化脚本
-- ===============================================

CREATE DATABASE IF NOT EXISTS tolink_rag_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE tolink_rag_db;

-- 1. 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id              VARCHAR(36)     PRIMARY KEY COMMENT '用户唯一标识 (UUID)',
    username        VARCHAR(64)    NOT NULL COMMENT '登录账号',
    password_hash  VARCHAR(255)    NOT NULL COMMENT '加密后的密码',
    nickname        VARCHAR(64)    COMMENT '用户昵称',
    email           VARCHAR(128)   COMMENT '邮箱地址',
    phone           VARCHAR(20)    COMMENT '手机号',
    avatar_url      VARCHAR(512)   COMMENT '头像地址',
    role            ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
    status          TINYINT        NOT NULL DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
    last_login_at   DATETIME       COMMENT '最后登录时间',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '系统用户表';

-- 2. LLM 系统级厂商配置表
CREATE TABLE IF NOT EXISTS llm_system_provider (
    id              VARCHAR(36)     PRIMARY KEY COMMENT '厂商唯一标识 (UUID)',
    provider_type   VARCHAR(32)    NOT NULL COMMENT '厂商类型：openai/claude/glm/deepseek',
    provider_name   VARCHAR(64)    NOT NULL COMMENT '厂商展示名称，如 "OpenAI"',
    api_base_url    VARCHAR(512)   NOT NULL COMMENT '官方默认 API 地址',
    supported_models JSON           COMMENT '支持模型与能力映射，如 {"gpt-4":["CHAT","OCR"]}',
    config_schema   JSON           COMMENT '配置参数 Schema（用于前端表单渲染）',
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE COMMENT '是否启用',
    priority        INT            NOT NULL DEFAULT 50 COMMENT '厂商优先级（1-100）',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_provider_type (provider_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'LLM 系统级厂商配置表';

-- 3. 用户级 LLM 配置表
CREATE TABLE IF NOT EXISTS llm_user_config (
    id                  VARCHAR(36)     PRIMARY KEY COMMENT '配置唯一标识 (UUID)',
    user_id             VARCHAR(36)     NOT NULL COMMENT '用户 ID',
    provider_id         VARCHAR(36)     NOT NULL COMMENT '关联 SystemProvider ID',
    provider_type       VARCHAR(32)     NOT NULL COMMENT '厂商类型快照',
    provider_name       VARCHAR(64)     NOT NULL COMMENT '厂商名称快照',
    config_name         VARCHAR(64)     NOT NULL COMMENT '用户自定义配置名称',
    api_key             VARCHAR(512)    NOT NULL COMMENT 'API Key（加密存储）',
    custom_api_base_url VARCHAR(512)    COMMENT '自定义 API 地址',
    model_name          VARCHAR(128)    NOT NULL COMMENT '具体模型名',
    priority            INT             NOT NULL DEFAULT 50 COMMENT '优先级 1-100',
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE COMMENT '是否启用',
    is_default          BOOLEAN         NOT NULL DEFAULT FALSE COMMENT '是否为默认配置',
    timeout_ms          INT             DEFAULT 60000 COMMENT '超时时间(毫秒)',
    max_retries         INT             DEFAULT 3 COMMENT '最大重试次数',
    stream_enabled      BOOLEAN         DEFAULT TRUE COMMENT '是否支持流式输出',
    capabilities        JSON            COMMENT '模型能力快照',
    extra_config        JSON            COMMENT '扩展配置',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_user_provider_model (user_id, provider_id, model_name),
    INDEX idx_user_active_default (user_id, is_active, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '用户级 LLM 配置表';

-- 4. 对话表
CREATE TABLE IF NOT EXISTS chat_conversation (
    id              VARCHAR(36)     PRIMARY KEY COMMENT '对话唯一标识',
    user_id         VARCHAR(36)     NOT NULL COMMENT '所属用户 ID',
    last_config_id  VARCHAR(36)     COMMENT '最后使用的 LLM 配置 ID',
    last_model_name VARCHAR(128)    COMMENT '最后使用的模型名快照',
    title           VARCHAR(255)    COMMENT '对话标题',
    is_pinned       BOOLEAN         DEFAULT FALSE COMMENT '是否置顶',
    is_deleted      BOOLEAN         DEFAULT FALSE COMMENT '软删除标记',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_active_list (user_id, is_deleted, is_pinned, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '对话表';

-- 5. 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id                  VARCHAR(36)     PRIMARY KEY COMMENT '消息唯一标识',
    conversation_id     VARCHAR(36)     NOT NULL COMMENT '所属对话 ID',
    config_id           VARCHAR(36)     COMMENT '产生该消息所使用的 LLM 配置 ID',
    model_name          VARCHAR(128)    COMMENT '模型名快照',
    role                VARCHAR(16)     NOT NULL COMMENT '角色：user/assistant/system',
    content             MEDIUMTEXT      NOT NULL COMMENT '消息内容',
    token_count         INT             DEFAULT 0 COMMENT '该条消息消耗的 Token 数',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '对话消息表';

-- 6. LLM 调用用量日志表
CREATE TABLE IF NOT EXISTS llm_usage_log (
    id                  VARCHAR(36)     PRIMARY KEY COMMENT '记录唯一标识',
    user_id             VARCHAR(36)     NOT NULL COMMENT '用户 ID',
    config_id           VARCHAR(36)     NOT NULL COMMENT '用户配置 ID',
    provider_type       VARCHAR(32)     NOT NULL COMMENT '厂商类型',
    model_name          VARCHAR(128)    NOT NULL COMMENT '模型名称',
    prompt_tokens       INT             NOT NULL COMMENT '输入 Token 数',
    completion_tokens   INT             NOT NULL COMMENT '输出 Token 数',
    total_tokens        INT             NOT NULL COMMENT '总 Token 数',
    latency_ms          INT             COMMENT '响应延迟(毫秒)',
    status              VARCHAR(16)     NOT NULL COMMENT '调用状态：success/failed/partial',
    error_message       VARCHAR(512)    COMMENT '错误信息',
    fallback_config_id  VARCHAR(36)     COMMENT '触发 Fallback 时记录原配置 ID',
    conversation_id     VARCHAR(36)     COMMENT '关联对话 ID',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user_date (user_id, created_at),
    INDEX idx_config_date (config_id, created_at),
    INDEX idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'LLM 调用用量日志表';

-- ===============================================
-- 初始化系统厂商数据
-- ===============================================
INSERT INTO llm_system_provider (id, provider_type, provider_name, api_base_url, supported_models, config_schema, is_active, priority)
VALUES
    ('prov_openai', 'openai', 'OpenAI', 'https://api.openai.com/v1',
     '{"gpt-4":["CHAT","OCR"], "gpt-3.5-turbo":["CHAT"]}',
     '{"temperature":{"type":"float","default":0.7,"min":0,"max":2}, "maxTokens":{"type":"int","default":1000,"min":1,"max":32000}}',
     TRUE, 100),
    ('prov_anthropic', 'claude', 'Anthropic Claude', 'https://api.anthropic.com/v1',
     '{"claude-3-opus":["CHAT","OCR"], "claude-3-sonnet":["CHAT"]}',
     '{"temperature":{"type":"float","default":0.7,"min":0,"max":2}, "maxTokens":{"type":"int","default":1000,"min":1,"max":32000}}',
     TRUE, 90),
    ('prov_zhipu', 'glm', '智谱 AI', 'https://open.bigmodel.cn/api/paas/v4',
     '{"glm-4":["CHAT"]}',
     '{"temperature":{"type":"float","default":0.7,"min":0,"max":2}}',
     TRUE, 80),
    ('prov_deepseek', 'deepseek', 'DeepSeek', 'https://api.deepseek.com/v1',
     '{"deepseek-chat":["CHAT"]}',
     '{"temperature":{"type":"float","default":0.7,"min":0,"max":2}}',
     TRUE, 70);

-- ===============================================
-- 初始化管理员账户 (密码: admin123)
-- ===============================================
INSERT INTO sys_user (id, username, password_hash, nickname, email, role, status)
VALUES ('admin', 'admin', '$2a$10$EasYxZ6ZB.YqlgDI8XnH4uuFow/KHNVnTLhXhOoBvhPTMK.FdrvEW', '系统管理员', 'admin@example.com', 'ADMIN', 1);
