-- ===============================================
-- toLink-Service 数据库初始化脚本 (MySQL 8.0)
-- 说明：
-- 1. 本文件只保留当前 Java 业务代码真实依赖的可执行建表语句
-- 2. 文件上传/解析链路当前以 document_parse_log 作为解析日志表既定事实
-- 3. knowledge_file_config 已退出运行时主链路，因此不再作为初始化表保留
-- ===============================================

CREATE DATABASE IF NOT EXISTS tolink_rag_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE tolink_rag_db;

-- 1. 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '用户唯一标识',
    username        VARCHAR(64)    NOT NULL COMMENT '登录账号',
    password_hash   VARCHAR(255)   NOT NULL COMMENT '加密后的密码',
    nickname        VARCHAR(64)    DEFAULT NULL COMMENT '用户昵称',
    email           VARCHAR(128)   DEFAULT NULL COMMENT '邮箱地址',
    phone           VARCHAR(20)    DEFAULT NULL COMMENT '手机号',
    avatar_url      VARCHAR(512)   DEFAULT NULL COMMENT '头像地址',
    role            ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
    status          TINYINT        NOT NULL DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
    last_login_at   DATETIME       DEFAULT NULL COMMENT '最后登录时间',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='系统用户表';

-- 2. LLM 系统级厂商配置表
CREATE TABLE IF NOT EXISTS llm_system_provider (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '厂商唯一标识',
    provider_type    VARCHAR(32)    NOT NULL COMMENT '厂商类型: openai/claude/glm/deepseek 等',
    provider_name    VARCHAR(64)    NOT NULL COMMENT '厂商展示名称',
    api_base_url     VARCHAR(512)   NOT NULL COMMENT '默认 API 地址',
    supported_models JSON           DEFAULT NULL COMMENT '支持模型与能力映射',
    config_schema    JSON           DEFAULT NULL COMMENT '配置参数 Schema',
    is_active        BOOLEAN        NOT NULL DEFAULT TRUE COMMENT '是否启用',
    priority         INT            NOT NULL DEFAULT 50 COMMENT '优先级',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_provider_type (provider_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='LLM 系统级厂商配置表';

-- 3. 用户级 LLM 配置表
CREATE TABLE IF NOT EXISTS llm_user_config (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '配置唯一标识',
    user_id              BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    provider_id          BIGINT UNSIGNED NOT NULL COMMENT '关联厂商 ID',
    provider_type        VARCHAR(32)     NOT NULL COMMENT '厂商类型快照',
    provider_name        VARCHAR(64)     NOT NULL COMMENT '厂商名称快照',
    config_name          VARCHAR(64)     NOT NULL COMMENT '用户自定义配置名称',
    api_key              VARCHAR(512)    NOT NULL COMMENT 'API Key（加密存储）',
    custom_api_base_url  VARCHAR(512)    DEFAULT NULL COMMENT '自定义 API 地址',
    model_name           VARCHAR(128)    NOT NULL COMMENT '具体模型名',
    priority             INT             NOT NULL DEFAULT 50 COMMENT '优先级',
    is_active            BOOLEAN         NOT NULL DEFAULT TRUE COMMENT '是否启用',
    is_default           BOOLEAN         NOT NULL DEFAULT FALSE COMMENT '是否为默认配置',
    timeout_ms           INT             DEFAULT 60000 COMMENT '超时时间(毫秒)',
    max_retries          INT             DEFAULT 3 COMMENT '最大重试次数',
    stream_enabled       BOOLEAN         DEFAULT TRUE COMMENT '是否启用流式响应',
    capabilities         JSON            DEFAULT NULL COMMENT '能力列表',
    extra_config         JSON            DEFAULT NULL COMMENT '扩展配置',
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_user_provider_model (user_id, provider_id, model_name),
    KEY idx_user_active_default (user_id, is_active, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='用户级 LLM 配置表';

-- 4. 数据集表
CREATE TABLE IF NOT EXISTS dataset (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '数据集唯一标识',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '所属用户 ID',
    name        VARCHAR(128)    NOT NULL COMMENT '数据集名称',
    description VARCHAR(512)    DEFAULT NULL COMMENT '数据集描述',
    status      VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '数据集状态',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_dataset_user_name (user_id, name),
    KEY idx_dataset_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='数据集表';

-- 5. 对话表
CREATE TABLE IF NOT EXISTS chat_conversation (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '对话唯一标识',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '所属用户 ID',
    dataset_id      BIGINT UNSIGNED NOT NULL COMMENT '所属数据集 ID',
    last_config_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '最后使用的 LLM 配置 ID',
    last_model_name VARCHAR(128)    DEFAULT NULL COMMENT '最后使用的模型名快照',
    title           VARCHAR(255)    DEFAULT NULL COMMENT '对话标题',
    is_pinned       BOOLEAN         DEFAULT FALSE COMMENT '是否置顶',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    KEY idx_chat_conversation_user_pinned_updated (user_id, is_pinned, updated_at),
    KEY idx_chat_conversation_dataset_updated (dataset_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='对话表';

-- 6. 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '消息唯一标识',
    conversation_id  BIGINT UNSIGNED NOT NULL COMMENT '所属对话 ID',
    config_id        BIGINT UNSIGNED DEFAULT NULL COMMENT '产生该消息所使用的 LLM 配置 ID',
    model_name       VARCHAR(128)    DEFAULT NULL COMMENT '模型名快照',
    role             VARCHAR(16)     NOT NULL COMMENT '角色: user/assistant/system',
    content          MEDIUMTEXT      NOT NULL COMMENT '消息内容',
    token_count      INT             DEFAULT 0 COMMENT '该条消息消耗的 Token 数',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='对话消息表';

-- 7. LLM 调用用量日志表
CREATE TABLE IF NOT EXISTS llm_usage_log (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '记录唯一标识',
    user_id            BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    config_id          BIGINT UNSIGNED NOT NULL COMMENT '用户配置 ID',
    provider_type      VARCHAR(32)     NOT NULL COMMENT '厂商类型',
    model_name         VARCHAR(128)    NOT NULL COMMENT '模型名称',
    prompt_tokens      INT             NOT NULL COMMENT '输入 Token 数',
    completion_tokens  INT             NOT NULL COMMENT '输出 Token 数',
    total_tokens       INT             NOT NULL COMMENT '总 Token 数',
    latency_ms         INT             DEFAULT NULL COMMENT '响应延迟(毫秒)',
    status             VARCHAR(16)     NOT NULL COMMENT '调用状态: success/failed/partial',
    error_message      VARCHAR(512)    DEFAULT NULL COMMENT '错误信息',
    fallback_config_id BIGINT UNSIGNED DEFAULT NULL COMMENT '触发 Fallback 时记录原配置 ID',
    conversation_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '关联对话 ID',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_user_date (user_id, created_at),
    KEY idx_config_date (config_id, created_at),
    KEY idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='LLM 调用用量日志表';

-- 8. 原始文档上传表
CREATE TABLE IF NOT EXISTS document_original_file (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '原始文档唯一标识',
    dataset_id        BIGINT UNSIGNED NOT NULL COMMENT '所属数据集 ID',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '上传用户 ID',
    original_filename VARCHAR(255)    NOT NULL COMMENT '用户上传时的原始文件名',
    file_suffix       VARCHAR(32)     NOT NULL COMMENT '标准化小写文件后缀',
    file_size         BIGINT UNSIGNED NOT NULL COMMENT '原始文件大小，单位字节',
    content_type      VARCHAR(128)    DEFAULT NULL COMMENT '上传请求中的 Content-Type',
    bucket_name       VARCHAR(64)     NOT NULL DEFAULT 'rag-raw' COMMENT '原文件私有存储桶',
    object_key        VARCHAR(512)    DEFAULT NULL COMMENT '私有 OSS 对象 Key',
    file_url          VARCHAR(1024)   DEFAULT NULL COMMENT 'Java 内部下载地址',
    upload_status     VARCHAR(20)     NOT NULL DEFAULT 'uploading' COMMENT '上传状态: uploading/success/failed',
    is_upload_success TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否上传成功',
    failure_reason    VARCHAR(512)    DEFAULT NULL COMMENT '上传失败原因编码',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_dataset_user_name_suffix (dataset_id, user_id, original_filename, file_suffix),
    KEY idx_document_original_dataset_created (dataset_id, created_at),
    KEY idx_document_original_user_created (user_id, created_at),
    KEY idx_document_original_upload_status (upload_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='知识库原始文档上传记录表';

-- 9. 文件解析日志表
CREATE TABLE IF NOT EXISTS document_parse_log (
    id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '解析任务记录主键',
    task_id                   VARCHAR(36)  NOT NULL COMMENT '解析任务业务唯一标识(UUID)',
    document_original_file_id BIGINT UNSIGNED NOT NULL COMMENT '原文件主键，对应 document_original_file.id',
    dataset_id                BIGINT UNSIGNED NOT NULL COMMENT '所属数据集 ID',
    user_id                   BIGINT UNSIGNED NOT NULL COMMENT '所属用户 ID',
    trigger_mode              VARCHAR(20)  NOT NULL COMMENT '触发方式: upload_auto/manual_retry',
    task_status               VARCHAR(16)  NOT NULL DEFAULT 'created' COMMENT '任务状态: created/processing/success/failed',
    failure_reason            VARCHAR(512) DEFAULT NULL COMMENT '业务化失败原因',
    dispatch_retry_count      INT          NOT NULL DEFAULT 0 COMMENT 'MQ 投递补偿重试次数',
    last_dispatch_error       VARCHAR(512) DEFAULT NULL COMMENT '最近一次投递异常摘要',
    last_dispatched_at        DATETIME     DEFAULT NULL COMMENT '最近一次 MQ 投递时间',
    parse_started_at          DATETIME     DEFAULT NULL COMMENT 'Python 开始解析时间',
    parse_finished_at         DATETIME     DEFAULT NULL COMMENT 'Python 结束解析时间',
    parse_duration_ms         BIGINT       DEFAULT NULL COMMENT '解析耗时，单位毫秒',
    created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_parse_task_id (task_id),
    KEY idx_parse_task_original_status (document_original_file_id, task_status, updated_at),
    KEY idx_parse_task_dataset_user (dataset_id, user_id, created_at),
    KEY idx_parse_task_status_retry (task_status, dispatch_retry_count, last_dispatched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='知识文件解析任务表';

-- 10. 文件解析表
CREATE TABLE IF NOT EXISTS document_parsed_file (
    id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '文件解析表主键',
    document_original_file_id BIGINT UNSIGNED NOT NULL COMMENT '原文件主键，对应 document_original_file.id',
    dataset_id                BIGINT UNSIGNED NOT NULL COMMENT '所属数据集 ID',
    user_id                   BIGINT UNSIGNED NOT NULL COMMENT '所属用户 ID',
    latest_parse_task_id      VARCHAR(36) DEFAULT NULL COMMENT '最新解析任务业务 ID，对应 document_parse_log.task_id',
    original_filename         VARCHAR(255) NOT NULL COMMENT '原文件名快照',
    parse_count               INT NOT NULL DEFAULT 0 COMMENT '累计成功解析次数',
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_document_parsed_original_file (document_original_file_id),
    KEY idx_document_parsed_dataset_user (dataset_id, user_id, updated_at),
    KEY idx_document_parsed_latest_task (latest_parse_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 AUTO_INCREMENT=10000 COMMENT='文件解析聚合表';


-- 11. 文档分片真值表
CREATE TABLE IF NOT EXISTS kb_document_chunk (
                                                 id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '分片记录主键ID',
                                                 chunk_id        VARCHAR(128)    NOT NULL COMMENT 'Chunk业务唯一标识，对应Qdrant Point ID',
                                                 doc_id          BIGINT UNSIGNED NOT NULL COMMENT '所属文档ID',
                                                 set_id          BIGINT UNSIGNED NOT NULL COMMENT '所属知识集ID',
                                                 user_id         BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
                                                 bucket_id       INT UNSIGNED    NOT NULL COMMENT 'Qdrant物理分桶编号',
                                                 content         TEXT            NOT NULL COMMENT 'Splitter最终产出的Chunk文本内容',
                                                 content_hash    CHAR(64)        NOT NULL COMMENT 'Chunk内容SHA-256哈希',
                                                 chunk_type      VARCHAR(32)     NOT NULL DEFAULT 'text' COMMENT '分片类型：text/paragraph/table/code_block/heading/mixed等',
                                                 start_line      INT             DEFAULT NULL COMMENT 'Chunk起始行号',
                                                 end_line        INT             DEFAULT NULL COMMENT 'Chunk结束行号',
                                                 chunk_index     INT             DEFAULT NULL COMMENT '当前文档内的Chunk顺序编号',
                                                 status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '索引状态：PENDING/INDEXING/INDEXED/FAILED',
                                                 error_msg       VARCHAR(512)    DEFAULT NULL COMMENT '最近一次失败原因',
                                                 retry_count     INT             NOT NULL DEFAULT 0 COMMENT '补偿重试次数',
                                                 last_retry_at   DATETIME        DEFAULT NULL COMMENT '最近一次补偿重试时间',
                                                 embedding_model VARCHAR(128)    DEFAULT NULL COMMENT '实际使用的Embedding模型名称',
                                                 create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                 update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                                 UNIQUE KEY uk_chunk_id (chunk_id),
                                                 KEY idx_user_set (user_id, set_id,doc_id),
                                                 KEY idx_bucket_status (bucket_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci AUTO_INCREMENT=10000 COMMENT='文档分片真值表，保存Chunk原文、归属字段、索引状态与补偿信息';



-- 设置自增起始值
ALTER TABLE sys_user AUTO_INCREMENT = 10000;
ALTER TABLE llm_system_provider AUTO_INCREMENT = 10000;
ALTER TABLE llm_user_config AUTO_INCREMENT = 10000;
ALTER TABLE dataset AUTO_INCREMENT = 10000;
ALTER TABLE chat_conversation AUTO_INCREMENT = 10000;
ALTER TABLE chat_message AUTO_INCREMENT = 10000;
ALTER TABLE llm_usage_log AUTO_INCREMENT = 10000;
ALTER TABLE document_original_file AUTO_INCREMENT = 10000;
ALTER TABLE document_parse_log AUTO_INCREMENT = 10000;
ALTER TABLE document_parsed_file AUTO_INCREMENT = 10000;
ALTER TABLE kb_document_chunk AUTO_INCREMENT = 10000;
