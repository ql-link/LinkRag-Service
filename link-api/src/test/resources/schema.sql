-- H2 Test Database Schema (MySQL Mode)

-- 1. 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT PRIMARY KEY COMMENT '用户唯一标识',
    username        VARCHAR(64) NOT NULL COMMENT '登录账号',
    password_hash   VARCHAR(255) NOT NULL COMMENT '加密后的密码',
    nickname        VARCHAR(64) COMMENT '用户昵称',
    email           VARCHAR(128) COMMENT '邮箱地址',
    phone           VARCHAR(20) COMMENT '手机号',
    avatar_url      VARCHAR(512) COMMENT '头像地址',
    role            VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
    last_login_at   DATETIME COMMENT '最后登录时间',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标记'
);

-- 2. LLM 系统级厂商配置表
CREATE TABLE IF NOT EXISTS llm_system_provider (
    id              BIGINT PRIMARY KEY COMMENT '厂商唯一标识',
    provider_type   VARCHAR(32) NOT NULL COMMENT '厂商类型：openai/claude/glm/deepseek',
    provider_name   VARCHAR(64) NOT NULL COMMENT '厂商展示名称',
    api_base_url    VARCHAR(512) NOT NULL COMMENT '官方默认 API 地址',
    supported_models JSON COMMENT '支持模型与能力映射',
    config_schema   JSON COMMENT '配置参数 Schema',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    priority        INT NOT NULL DEFAULT 50 COMMENT '厂商优先级',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 3. 用户级 LLM 配置表
CREATE TABLE IF NOT EXISTS llm_user_config (
    id                  BIGINT PRIMARY KEY COMMENT '配置唯一标识',
    user_id             BIGINT NOT NULL COMMENT '用户 ID',
    provider_id         BIGINT NOT NULL COMMENT '关联 SystemProvider ID',
    provider_type       VARCHAR(32) NOT NULL COMMENT '厂商类型快照',
    provider_name       VARCHAR(64) NOT NULL COMMENT '厂商名称快照',
    config_name         VARCHAR(64) NOT NULL COMMENT '用户自定义配置名称',
    api_key             VARCHAR(512) NOT NULL COMMENT 'API Key',
    custom_api_base_url VARCHAR(512) COMMENT '自定义 API 地址',
    model_name          VARCHAR(128) NOT NULL COMMENT '具体模型名',
    priority            INT NOT NULL DEFAULT 50 COMMENT '优先级',
    is_active           BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    is_default          BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为默认配置',
    timeout_ms          INT DEFAULT 60000 COMMENT '超时时间',
    max_retries         INT DEFAULT 3 COMMENT '最大重试次数',
    stream_enabled      BOOLEAN DEFAULT TRUE COMMENT '是否支持流式输出',
    capabilities        JSON COMMENT '模型能力快照',
    extra_config        JSON COMMENT '扩展配置',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 4. 对话表
CREATE TABLE IF NOT EXISTS chat_conversation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '对话唯一标识',
    user_id         BIGINT NOT NULL COMMENT '所属用户 ID',
    last_config_id  BIGINT COMMENT '最后使用的 LLM 配置 ID',
    last_model_name VARCHAR(128) COMMENT '最后使用的模型名快照',
    title           VARCHAR(255) COMMENT '对话标题',
    is_pinned       BOOLEAN DEFAULT FALSE COMMENT '是否置顶',
    is_deleted      BOOLEAN DEFAULT FALSE COMMENT '软删除标记',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 5. 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息唯一标识',
    conversation_id     BIGINT NOT NULL COMMENT '所属对话 ID',
    config_id           BIGINT COMMENT '产生该消息所使用的 LLM 配置 ID',
    model_name          VARCHAR(128) COMMENT '模型名快照',
    role                VARCHAR(16) NOT NULL COMMENT '角色：user/assistant/system',
    content             TEXT NOT NULL COMMENT '消息内容',
    token_count         INT DEFAULT 0 COMMENT 'Token 数',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. LLM 调用用量日志表
CREATE TABLE IF NOT EXISTS llm_usage_log (
    id                  BIGINT PRIMARY KEY COMMENT '记录唯一标识',
    user_id             BIGINT NOT NULL COMMENT '用户 ID',
    config_id           BIGINT NOT NULL COMMENT '用户配置 ID',
    provider_type       VARCHAR(32) NOT NULL COMMENT '厂商类型',
    model_name          VARCHAR(128) NOT NULL COMMENT '模型名称',
    prompt_tokens       INT NOT NULL COMMENT '输入 Token 数',
    completion_tokens   INT NOT NULL COMMENT '输出 Token 数',
    total_tokens        INT NOT NULL COMMENT '总 Token 数',
    latency_ms          INT COMMENT '响应延迟',
    status              VARCHAR(16) NOT NULL COMMENT '调用状态',
    error_message       VARCHAR(512) COMMENT '错误信息',
    fallback_config_id  BIGINT COMMENT '触发 Fallback 时记录原配置 ID',
    conversation_id     BIGINT COMMENT '关联对话 ID',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_sys_user_username ON sys_user(username);
CREATE INDEX idx_sys_user_email ON sys_user(email);
CREATE INDEX idx_llm_user_config_user_id ON llm_user_config(user_id);
CREATE INDEX idx_chat_conversation_user_id ON chat_conversation(user_id);
CREATE INDEX idx_chat_message_conversation_id ON chat_message(conversation_id);
CREATE INDEX idx_llm_usage_log_user_id ON llm_usage_log(user_id);
