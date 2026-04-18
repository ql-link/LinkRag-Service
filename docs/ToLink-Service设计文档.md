# ToLink Service 技术设计文档

> 项目名称：ToLink Service
> 更新时间：2026-04-14
> 状态：开发中
> 描述：多 LLM 接入模块的 Java 管理端，负责用户交互和配置管理

---

## 一、项目概述

### 1.1 项目定位

ToLink Service 是整个系统的 Java 管理端，承载用户交互和配置管理的职责。与 Python 执行端配合工作，两者通过共用同一个 MySQL 数据库和 Redis 缓存实现数据共享。

**核心职责**：
- 用户账户管理（注册、登录、信息维护）
- LLM 厂商配置管理（系统级和用户级）
- 对话历史管理（创建、查询、删除）
- 用量统计查询（汇总、明细）

**不涉及的职责**：
- 任何 LLM API 调用
- RAG 检索和生成逻辑
- 用量日志的写入（由 Python 执行端负责）

### 1.2 协作模式

```
┌─────────────────────────────────────────────────────────────┐
│                     Java 管理端 (ToLink Service)              │
│                                                             │
│  【用户可见的管理功能】                                         │
│   · 用户注册 / 登录 / 登出                                    │
│   · 用户个人信息管理                                          │
│   · LLM 配置增删改查（用户级）                                │
│   · 系统厂商管理（管理员）                                     │
│   · 对话创建 / 列表 / 软删除                                  │
│   · 用量统计查询                                              │
│                                                             │
│  【数据写入】                                                 │
│   · 用户信息写入 MySQL                                        │
│   · LLM 用户配置写入 MySQL（同时失效 Redis 缓存）              │
│   · 对话和消息写入 MySQL                                       │
│                                                             │
│  【数据读取】                                                 │
│   · 从 MySQL 读取结构化数据                                    │
│   · 从 Redis 读取缓存的配置（性能优化）                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ 共用数据库
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Python RAG 执行端                         │
│                                                             │
│  【后台执行功能】                                             │
│   · 读取 Redis 缓存的 LLM 配置（未命中则查 MySQL 回填）        │
│   · 调用各厂商 LLM API                                        │
│   · RAG 检索和生成                                           │
│   · 写入对话消息到 MySQL                                      │
│   · 写入用量日志到 MySQL                                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、技术栈详情

### 2.1 核心技术选型

| 组件 | 版本 | 选型理由 |
|------|------|----------|
| Java | 17 | LTS 版本，性能提升，支持新语法特性 |
| Spring Boot | 2.5.3 | 成熟稳定，社区活跃，与其他组件兼容性好 |
| Spring Security | 2.5.3 | 官方安全框架，BCrypt 密码加密开箱即用 |
| MySQL | 8.0 | 远端部署，支持 JSON 类型，UTF-8 MB4 完整支持 |
| Redis | 7.x | Token 存储 + 配置缓存，内存数据库高性能 |
| sa-token | 1.39.0 | 轻量级认证框架，学习成本低，与 Spring Boot 集成简单 |

### 2.2 中间件依赖

| 中间件 | 用途 | 连接信息 |
|--------|------|----------|
| MySQL | 主数据库，存储所有业务数据 | 36.213.180.176:3306 |
| Redis | 缓存层 + Token 存储 | 本地或远端 |

### 2.3 数据层框架

| 框架 | 版本 | 用途 |
|------|------|------|
| MyBatis-Plus | 3.5.2 / 3.4.2 | ORM 框架，减少手写 SQL，内置分页、逻辑删除支持 |
| Druid | 1.1.22 | 数据库连接池，监控功能强，启动检查完善 |
| PageHelper | 1.2.12 | 分页插件，与 MyBatis-Plus 配合使用 |

---

## 三、环境配置

### 3.1 服务器信息

| 环境 | MySQL 地址 | 说明 |
|------|------------|------|
| 开发环境 | localhost:3306 | 本地 Docker 部署 |
| 生产环境 | 36.213.180.176:3306 | 远端云服务器 |

### 3.2 数据库配置

数据库名称为 `tolink_rag_db`，采用 UTF-8 MB4 字符集，可完整存储 emoji 和特殊字符。

连接参数说明：
- `useUnicode=true&characterEncoding=utf-8`：显式指定字符编码
- `useSSL=false`：生产环境根据实际情况决定
- `serverTimezone=Asia/Shanghai`：统一时区，避免时间转换问题
- `allowPublicKeyRetrieval=true`：解决 MySQL 8.x 连接时的公钥检索问题

### 3.3 Redis 配置

Redis 用于两个主要场景：

**场景一：Token 存储**
- sa-token 将登录 Token 存储在 Redis 中
- 支持 Token 黑名单机制（登出时写入黑名单）
- 支持集群部署下的 Session 共享

**场景二：业务数据缓存**
- 用户信息缓存，TTL 7 天
- 用户 LLM 配置缓存，TTL 7 天
- 系统厂商信息缓存，TTL 30 天
- 用户默认配置 ID 缓存，TTL 30 天

### 3.4 环境变量清单

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| DB_HOST | localhost | 数据库地址 |
| DB_PORT | 3306 | 数据库端口 |
| DB_NAME | tolink_rag_db | 数据库名称 |
| DB_USER | root | 数据库用户名 |
| DB_PASSWORD | - | 数据库密码（必须设置） |
| REDIS_HOST | localhost | Redis 地址 |
| REDIS_PORT | 6379 | Redis 端口 |
| REDIS_PASSWORD | - | Redis 密码（如有） |
| REDIS_DB | 0 | Redis 数据库编号 |
| LLM_SECRET | - | API Key 加密密钥（64位十六进制） |

---

## 四、安全设计

### 4.1 密码安全

**存储策略：BCrypt 单向哈希**

用户密码绝不以明文形式存储。采用 BCrypt 算法进行单向哈希，特点如下：

- **不可逆**：无法从哈希值反推原始密码
- **自带盐**：每个密码都有随机生成的盐值，防止彩虹表攻击
- **可配置强度**：默认强度 10（2^10 次迭代），平衡安全性和性能
- **防暴力**：迭代次数多，单次验证较慢，有效防止暴力破解

**校验流程**：
1. 用户输入密码
2. 从数据库读取该用户的 BCrypt 哈希值
3. 使用 BCrypt 的 matches() 方法比对
4. 返回校验结果

**初始密码**：
- 管理员初始用户名：admin
- 初始密码：admin123
- 生产环境务必在首次登录后强制修改

### 4.2 API Key 安全

**加密策略：AES-256-GCM 对称加密**

LLM 配置中的 API Key 需要加密存储，原因：

- 数据库可能面临 SQL 注入等风险
- 运维人员不应看到用户的敏感信息
- 数据泄露时降低损失

**加密细节**：
- **算法**：AES-256-GCM，Galois/Counter Mode，带认证标签
- **密钥**：64字符十六进制字符串（转换为32字节）
- **IV**：每次加密随机生成12字节，拼接在密文前
- **存储格式**：Base64(IV + 密文 + 认证标签)

**加解密流程**：
```
加密：明文 → AES-256-GCM(随机IV, 密钥) → Base64编码 → 存储
解密：读取 → Base64解码 → AES-256-GCM解密 → 明文
```

**密钥管理**：
- 密钥通过环境变量 LLM_SECRET 注入
- Java 和 Python 两端使用相同密钥
- 密钥变更需两端同时更新

### 4.3 RBAC 权限管理

**角色：ADMIN / USER**

系统内置两个角色，普通用户注册后自动分配 `USER` 角色，管理员由数据库初始化脚本预置。

**sa-token 集成**：
- 通过 `StpInterfaceImpl` 实现 `StpInterface` 接口，每次鉴权时从 Redis 缓存加载用户角色
- 缓存未命中则查 MySQL 并回填缓存，TTL 24 小时
- 使用 `@SaCheckRole("ADMIN")` 注解保护管理员接口

**权限注解**：
- `@SaCheckLogin`：验证登录状态
- `@SaCheckRole("ADMIN")`：验证管理员角色

### 4.4 Token 安全

**认证策略：sa-token UUID 风格 Token**

- **Token 风格**：简短的 UUID 格式，如 `4f180049-c6e9-47e7-9a41-8571c9e5b87f`
- **有效期**：7 天（604800 秒）
- **存储位置**：Redis
- **传输方式**：强制使用 Header 传输（关闭 Cookie）
- **Header 名称**：satoken

**多端登录策略**：
- 同一账号支持多地登录（isConcurrent=true）
- 但 Token 不共享（isShare=false），后登录会使前者失效
- 登出时 Token 加入黑名单，在过期前无法使用

---

## 五、数据库设计

### 5.1 设计原则

**无外键约束**：
- 应用层保证数据完整性
- 便于 Python 和 Java 两端维护
- 避免级联删除带来的复杂性

**冗余设计**：
- 厂商名称、模型名称等字段在用户配置表中冗余存储
- 避免联表查询，提升查询性能
- 变更时通过应用层同步更新

**逻辑删除**：
- 对话表使用逻辑删除（is_deleted 字段）
- 保留历史数据，支持数据恢复
- MyBatis-Plus 自动处理删除和查询条件

### 5.2 表结构详解

#### 5.2.1 系统用户表 (sys_user)

**用途**：存储所有系统用户信息，支持管理员和普通用户两种角色。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PRIMARY KEY | 用户唯一标识，使用 UUID，登录后作为 Token 值 |
| username | VARCHAR(64) | NOT NULL, UNIQUE | 登录账号，全局唯一 |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt 加密后的密码哈希 |
| nickname | VARCHAR(64) | - | 用户昵称，用于展示 |
| email | VARCHAR(128) | UNIQUE | 邮箱地址，全局唯一 |
| phone | VARCHAR(20) | - | 手机号 |
| avatar_url | VARCHAR(512) | - | 头像 URL 地址 |
| role | ENUM | NOT NULL | 角色：ADMIN（管理员）或 USER（普通用户） |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：1=正常，0=禁用 |
| last_login_at | DATETIME | - | 最后登录时间，用于审计 |
| created_at | DATETIME | NOT NULL | 账户创建时间 |
| updated_at | DATETIME | NOT NULL | 账户信息最后修改时间 |

**索引说明**：
- `uk_username`：唯一索引，加速用户名登录查询
- `uk_email`：唯一索引，邮箱登录或找回密码场景

#### 5.2.2 LLM 系统级厂商配置表 (llm_system_provider)

**用途**：存储系统支持的 LLM 厂商元信息，由管理员维护，用户不可见。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PRIMARY KEY | 厂商唯一标识 |
| provider_type | VARCHAR(32) | NOT NULL, UNIQUE | 厂商类型标识符，如 openai、claude、glm、deepseek |
| provider_name | VARCHAR(64) | NOT NULL | 面向用户的中文展示名称，如"OpenAI"、"智谱 AI" |
| api_base_url | VARCHAR(512) | NOT NULL | 官方默认 API 地址前缀 |
| supported_models | JSON | - | 支持的模型列表，格式：`{"gpt-4":["CHAT","OCR"], "gpt-3.5-turbo":["CHAT"]}` |
| config_schema | JSON | - | 配置参数 Schema，用于前端表单动态渲染 |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用，禁用后用户不可见该厂商 |
| priority | INT | NOT NULL, DEFAULT 50 | 优先级 1-100，数字越大优先级越高 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

**支持的厂商类型**：
- `openai`：OpenAI GPT 系列
- `claude`：Anthropic Claude 系列
- `glm`：智谱 AI GLM 系列
- `deepseek`：DeepSeek 系列

**config_schema 示例**：
```json
{
  "temperature": {
    "type": "float",
    "default": 0.7,
    "min": 0,
    "max": 2,
    "label": "温度"
  },
  "maxTokens": {
    "type": "int",
    "default": 1000,
    "min": 1,
    "max": 32000,
    "label": "最大 Token 数"
  }
}
```

#### 5.2.3 用户级 LLM 配置表 (llm_user_config)

**用途**：存储用户自己配置的 LLM API 密钥和参数，一个用户可配置多个厂商的多个模型。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PRIMARY KEY | 配置唯一标识 |
| user_id | VARCHAR(36) | NOT NULL | 所属用户 ID |
| provider_id | VARCHAR(36) | NOT NULL | 关联的系统厂商 ID |
| provider_type | VARCHAR(32) | NOT NULL | 厂商类型快照，避免联表查询 |
| provider_name | VARCHAR(64) | NOT NULL | 厂商名称快照 |
| config_name | VARCHAR(64) | NOT NULL | 用户自定义的配置名称，如"我的 GPT-4" |
| api_key | VARCHAR(512) | NOT NULL | 加密后的 API Key |
| custom_api_base_url | VARCHAR(512) | - | 自定义 API 地址，覆盖系统默认（用于代理） |
| model_name | VARCHAR(128) | NOT NULL | 具体模型名称，如 gpt-4、claude-3-opus |
| priority | INT | NOT NULL, DEFAULT 50 | 优先级 1-100，多配置时优先使用高优先级 |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用，禁用后不会自动选用 |
| is_default | BOOLEAN | NOT NULL, DEFAULT FALSE | 是否为用户的默认配置，每个用户只能有一个默认 |
| timeout_ms | INT | DEFAULT 60000 | 请求超时时间，毫秒 |
| max_retries | INT | DEFAULT 3 | 调用失败时的最大重试次数 |
| stream_enabled | BOOLEAN | DEFAULT TRUE | 是否支持流式输出（SSE） |
| capabilities | JSON | - | 模型能力快照，从系统厂商配置复制 |
| extra_config | JSON | - | 额外参数，格式同 config_schema，可覆盖系统默认值 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

**索引说明**：
- `uk_user_provider_model`：唯一索引，防止用户重复添加同一厂商的同一模型
- `idx_user_active_default`：复合索引，加速"获取用户默认配置"查询

#### 5.2.4 对话表 (chat_conversation)

**用途**：存储用户的对话会话，支持置顶和软删除。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PRIMARY KEY | 对话唯一标识 |
| user_id | VARCHAR(36) | NOT NULL | 所属用户 ID |
| last_config_id | VARCHAR(36) | - | 该对话最后使用的 LLM 配置 ID（用于下次打开时默认回显） |
| last_model_name | VARCHAR(128) | - | 最后使用的模型名称快照 |
| title | VARCHAR(255) | - | 对话标题，用户可自定义 |
| is_pinned | BOOLEAN | DEFAULT FALSE | 是否置顶，置顶对话始终显示在列表前面 |
| is_deleted | BOOLEAN | DEFAULT FALSE | 软删除标记，删除后不显示但保留数据 |
| created_at | DATETIME | NOT NULL | 对话创建时间 |
| updated_at | DATETIME | NOT NULL | 最后一条消息的时间，用于列表排序 |

**索引说明**：
- `idx_user_active_list`：复合索引，包含 (user_id, is_deleted, is_pinned, updated_at)，覆盖用户对话列表查询

#### 5.2.5 对话消息表 (chat_message)

**用途**：存储对话中的每一条消息，包括用户消息、助手回复、系统消息。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PRIMARY KEY | 消息唯一标识 |
| conversation_id | VARCHAR(36) | NOT NULL | 所属对话 ID |
| config_id | VARCHAR(36) | - | 产生该消息所使用的 LLM 配置 ID |
| model_name | VARCHAR(128) | - | 模型名称快照 |
| role | VARCHAR(16) | NOT NULL | 消息角色：user（用户）、assistant（助手）、system（系统） |
| content | MEDIUMTEXT | NOT NULL | 消息内容，使用 MEDIUMTEXT 支持长文本 |
| token_count | INT | DEFAULT 0 | 该条消息消耗的 Token 数 |
| created_at | DATETIME | NOT NULL | 消息创建时间 |

**索引说明**：
- `idx_conversation_created`：复合索引，按 (conversation_id, created_at) 排序，支持分页查询

#### 5.2.6 用量日志表 (llm_usage_log)

**用途**：记录每次 LLM API 调用的用量信息，用于统计和计费。由 Python 执行端写入，Java 管理端仅读取。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VARCHAR(36) | PRIMARY KEY | 记录唯一标识 |
| user_id | VARCHAR(36) | NOT NULL | 用户 ID |
| config_id | VARCHAR(36) | NOT NULL | 使用的用户配置 ID |
| provider_type | VARCHAR(32) | NOT NULL | 厂商类型快照 |
| model_name | VARCHAR(128) | NOT NULL | 模型名称快照 |
| prompt_tokens | INT | NOT NULL | 输入 Token 数 |
| completion_tokens | INT | NOT NULL | 输出 Token 数 |
| total_tokens | INT | NOT NULL | 总 Token 数 |
| latency_ms | INT | - | 本次调用耗时，毫秒 |
| status | VARCHAR(16) | NOT NULL | 调用状态：success（成功）、failed（失败）、partial（部分成功） |
| error_message | VARCHAR(512) | - | 错误信息，失败时填写 |
| fallback_config_id | VARCHAR(36) | - | 触发 Fallback 时，记录原始配置 ID |
| conversation_id | VARCHAR(36) | - | 关联的对话 ID |
| created_at | DATETIME | NOT NULL | 记录创建时间 |

**索引说明**：
- `idx_user_date`：按 (user_id, created_at) 索引，用户用量统计主查询
- `idx_config_date`：按 (config_id, created_at) 索引，按配置查用量
- `idx_conversation_id`：按 conversation_id 索引，按对话查用量

---

## 六、项目结构

### 6.1 模块架构

项目采用 Maven 多模块结构，按职责划分为以下模块：

| 模块 | 打包方式 | 依赖关系 | 职责 |
|------|---------|---------|------|
| link-service | jar | link-api, link-mapper, link-components | Spring Boot 启动模块，包含 Service 层业务逻辑 |
| link-api | jar | link-core, link-model | Controller 层，对外暴露 HTTP 接口、参数校验、路由分发 |
| link-mapper | jar | link-model | Mapper 层，MyBatis-Plus Mapper 接口 + XML，数据库交互 |
| link-core | jar | link-model | 核心层，DTO、异常定义、工具类（ApiKeyEncryptService、AuthContext） |
| link-model | jar | 无 | 数据模型层，Entity 实体类、Enum 枚举 |
| link-components | jar | 无 | 通用组件中台，封装中间件集成（Kafka、MQ 等） |

**模块依赖方向图**：
```
link-model ←── link-core ←── link-api ──┐
     ↑                                   ├──► link-service (主应用/启动模块)
link-model ←── link-mapper ─────────────┘
                    ↑
             link-components
```

> `link-service` 是 Spring Boot 启动模块，不直接写 Mapper，通过依赖 `link-mapper` 和 `link-api` 实现完整解耦。

### 6.2 包结构详解

#### link-service 模块
```
link-service/src/main/java/com/qingluo/link/
│
└── LinkServiceApplication.java    # Spring Boot 启动类
```

#### link-api 模块
```
link-api/src/main/java/com/qingluo/link/
│
├── stp/                            # sa-token RBAC 集成
│   └── StpInterfaceImpl.java      # 角色权限加载实现
└── controller/                    # 控制层（HTTP 请求处理）
    ├── AuthController.java         # 认证：登录/注册/登出
    ├── UserController.java         # 用户：个人信息、修改资料
    ├── ConfigController.java        # LLM 配置：增删改查
    ├── ChatController.java          # 对话：创建/列表/删除/消息
    ├── UsageController.java         # 用量：汇总/日度/明细
    ├── AdminController.java         # 管理员：用户管理、系统厂商管理
    └── GlobalExceptionHandler.java  # 全局异常处理
```

#### link-service 模块（Service 层）
```
link-service/src/main/java/com/qingluo/link/
│
└── service/                         # 服务接口与实现
    │
    ├── cache/                       # 缓存服务
    │   ├── UserCacheService.java    # 用户信息缓存
    │   └── UserCacheServiceImpl.java
    │
    ├── service/                     # 服务接口
    │   ├── AuthService.java
    │   ├── AdminUserService.java   # 管理员用户管理
    │   ├── AdminProviderService.java # 管理员厂商管理
    │   ├── SystemProviderService.java
    │   ├── UserLLMConfigService.java
    │   ├── ChatService.java
    │   └── UsageQueryService.java
    │
    └── impl/                        # 服务实现
        ├── AuthServiceImpl.java
        ├── AdminUserServiceImpl.java
        ├── AdminProviderServiceImpl.java
        ├── SystemProviderServiceImpl.java
        ├── UserLLMConfigServiceImpl.java
        ├── ChatServiceImpl.java
        └── UsageQueryServiceImpl.java
```

#### link-mapper 模块
```
link-mapper/src/main/java/com/qingluo/link/
│
└── mapper/                          # 数据访问层（MyBatis-Plus）
    ├── SysUserMapper.java
    ├── SystemProviderMapper.java
    ├── UserLLMConfigMapper.java
    ├── ChatConversationMapper.java
    ├── ChatMessageMapper.java
    └── UsageLogMapper.java

link-mapper/src/main/resources/mapper/  # MyBatis XML 映射文件
    └── *.xml
```

#### link-core 模块
```
link-core/src/main/java/com/qingluo/link/core/
│
├── exception/                          # 异常体系
│   ├── BusinessException.java          # 异常基类
│   ├── AuthException.java              # 认证异常（401）
│   ├── ForbiddenException.java         # 无权限异常（403）
│   ├── NotFoundException.java          # 资源不存在（404）
│   ├── ConflictException.java          # 冲突异常（409）
│   ├── SystemException.java            # 系统异常（500）
│   └── ErrorResponse.java              # 错误响应 DTO
│
└── util/                               # 工具类
    ├── ApiKeyEncryptService.java        # AES-256-GCM 加解密
    ├── AuthContext.java                 # 认证上下文（获取当前用户 ID）
    ├── DateUtil.java                    # 日期工具
    ├── JsonUtil.java                    # JSON 序列化工具（fastjson）
    └── StringUtil.java                  # 字符串工具
```

#### link-model 模块
```
link-model/src/main/java/com/qingluo/link/model/
│
├── dto/
│   ├── entity/                          # 实体层（与数据库表一一对应）
│   │   ├── SysUser.java                 # 系统用户
│   │   ├── SystemProvider.java          # LLM 系统厂商配置
│   │   ├── UserLLMConfig.java           # 用户 LLM 配置（含加密 apiKey）
│   │   ├── ChatConversation.java        # 对话（含逻辑删除）
│   │   ├── ChatMessage.java             # 对话消息
│   │   └── UsageLog.java                # 用量日志
│   │
│   ├── request/                         # 入参对象（带 Validation 注解）
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── UpdateProfileRequest.java   # 修改个人资料
│   │   ├── UpdateUserStatusRequest.java # 管理员修改用户状态
│   │   ├── UpdateUserRoleRequest.java   # 管理员修改用户角色
│   │   ├── CreateProviderRequest.java   # 管理员创建厂商
│   │   ├── UpdateProviderRequest.java   # 管理员更新厂商
│   │   ├── CreateConfigRequest.java
│   │   ├── UpdateConfigRequest.java
│   │   ├── CreateConversationRequest.java
│   │   └── SaveMessageRequest.java
│   │
│   └── response/                        # 出参对象
│       ├── Result.java                  # 统一响应包装
│       ├── PageResult.java              # 分页响应
│       ├── AuthResult.java              # 认证结果（含 token）
│       ├── UserProfileDTO.java          # 用户信息
│       ├── UserLLMConfigDTO.java        # LLM 配置（脱敏 apiKey）
│       ├── ConversationDTO.java         # 对话信息
│       ├── MessageDTO.java              # 消息信息
│       ├── UsageSummaryDTO.java         # 用量汇总
│       ├── DailyUsageDTO.java           # 日度用量
│       └── UsageLogDTO.java             # 用量明细
│
└── enums/                               # 枚举类
    ├── ErrorCode.java                   # 错误码（含 code、message、httpStatus）
    └── UserRole.java                    # 用户角色（ADMIN / USER）
```

#### link-components 模块
```
link-components/src/main/java/com/qingluo/link/components/
    # 通用组件中台，预留中间件集成扩展（Kafka、MQ 等）
```

### 6.3 资源文件结构

```
link-service/src/main/
└── resources/
    └── application.yml             # 主配置文件
        包含：服务器端口、数据源、Redis、MyBatis-Plus、sa-token 等配置

link-mapper/src/main/
├── java/...                         # Mapper 源码
└── resources/
    └── mapper/                      # MyBatis XML 映射文件
        └── *.xml                    # 各实体的复杂查询映射

docs/db/
├── schema.sql                       # 数据库建表脚本
└── init.sql                         # 初始化数据脚本（管理员账户、LLM 厂商配置）
```

---

## 七、API 接口详解

### 7.1 接口规范

**Base URL**：`http://{host}:8080/api/v1`

**认证方式**：除登录/注册接口外，所有接口需在 Header 中携带 Token：
```
Authorization: Bearer {accessToken}
```

**统一响应格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": {...}
}
```

**分页响应格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

**分页参数说明**：
- `page`：页码，从 1 开始
- `pageSize`：每页条数，默认 20，最大 100

### 7.2 认证接口

#### 7.2.1 用户登录

**接口**：`POST /api/v1/auth/login`

**功能描述**：用户使用用户名和密码登录，验证成功后返回 Token。

**请求体**：
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "4f180049-c6e9-47e7-9a41-8571c9e5b87f",
    "tokenType": "Bearer",
    "expiresIn": 604800,
    "userId": "f2d0ba5c-9123-47d1-92f4-e2456837e561"
  }
}
```

**错误响应**：
- 用户不存在（404）：`{"code": 20001, "message": "用户不存在"}`
- 密码错误（401）：`{"code": 20002, "message": "密码错误"}`
- 账号禁用（403）：`{"code": 20003, "message": "账号已被禁用"}`

#### 7.2.2 用户注册

**接口**：`POST /api/v1/auth/register`

**功能描述**：新用户注册，自动分配普通用户角色。

**请求体**：
```json
{
  "username": "alice",
  "password": "password123",
  "nickname": "Alice",
  "email": "alice@example.com"
}
```

**成功响应（201）**：
```json
{
  "code": 201,
  "message": "success",
  "data": {
    "id": "uuid-xxx",
    "username": "alice"
  }
}
```

**错误响应**：
- 用户名已存在（409）：`{"code": 10009, "message": "用户名已存在"}`

#### 7.2.3 退出登录

**接口**：`POST /api/v1/auth/logout`

**功能描述**：当前 Token 加入黑名单，后续请求无效。

**请求头**：`Authorization: Bearer {token}`

**成功响应**：204 No Content

### 7.3 用户接口

#### 7.3.1 获取当前用户信息

**接口**：`GET /api/v1/user/profile`

**功能描述**：获取当前登录用户详细信息。

**请求头**：`Authorization: Bearer {token}`

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "f2d0ba5c-9123-47d1-92f4-e2456837e561",
    "username": "admin",
    "nickname": "系统管理员",
    "email": "admin@example.com",
    "role": "ADMIN"
  }
}
```

#### 7.3.2 修改个人资料

**接口**：`PATCH /api/v1/user/profile`

**功能描述**：修改当前登录用户的个人资料，变更后自动驱逐 Redis 缓存。

**请求头**：`Authorization: Bearer {token}`

**请求体**（所有字段均可选，仅传入需要修改的字段）：
```json
{
  "nickname": "新昵称",
  "email": "new@example.com",
  "phone": "13800138000",
  "avatarUrl": "https://example.com/avatar.png"
}
```

**成功响应（200）**：`{"code": 200, "message": "success"}`

**说明**：
- `username`、`role`、`status` 不可修改
- 变更后自动调用 `UserCacheService.evict()` 双删缓存

### 7.4 管理员用户接口

> 以下接口需 `Authorization: Bearer {token}`，且用户角色为 `ADMIN`，非管理员访问返回 403。

#### 7.4.1 查询用户列表

**接口**：`GET /api/v1/admin/users`

**功能描述**：分页查询所有用户列表，按创建时间倒序。

**请求头**：`Authorization: Bearer {token}`

**查询参数**：
- `page`（可选，默认 1）：页码
- `size`（可选，默认 10）：每页条数

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1,
        "username": "alice",
        "nickname": "Alice",
        "email": "alice@example.com",
        "role": "USER",
        "status": 1
      }
    ],
    "total": 42,
    "page": 1,
    "pageSize": 10
  }
}
```

#### 7.4.2 修改用户状态

**接口**：`PATCH /api/v1/admin/users/{id}/status`

**功能描述**：启用或禁用指定用户。

**请求体**：
```json
{
  "status": 0
}
```
- `1` = 启用，`0` = 禁用

**成功响应（200）**：`{"code": 200, "message": "success"}`

**说明**：禁用后该用户无法登录。

#### 7.4.3 修改用户角色

**接口**：`PATCH /api/v1/admin/users/{id}/role`

**功能描述**：将普通用户提升为管理员，或降级为普通用户。

**请求体**：
```json
{
  "role": "ADMIN"
}
```
- 有效值：`ADMIN`、`USER`

**成功响应（200）**：`{"code": 200, "message": "success"}`

### 7.5 管理员系统厂商接口

> 以下接口需 `Authorization: Bearer {token}`，且用户角色为 `ADMIN`。

#### 7.5.1 查询厂商列表

**接口**：`GET /api/v1/admin/providers`

**功能描述**：分页查询所有 LLM 厂商列表，按优先级倒序。

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": 10001,
        "providerType": "openai",
        "providerName": "OpenAI",
        "apiBaseUrl": "https://api.openai.com/v1",
        "isActive": true,
        "priority": 100
      }
    ],
    "total": 4,
    "page": 1,
    "pageSize": 10
  }
}
```

#### 7.5.2 创建厂商

**接口**：`POST /api/v1/admin/providers`

**请求体**：
```json
{
  "providerType": "openai",
  "providerName": "OpenAI",
  "apiBaseUrl": "https://api.openai.com/v1",
  "supportedModels": "{\"gpt-4\":[\"CHAT\"]}",
  "configSchema": "{\"temperature\":{\"type\":\"float\",\"default\":0.7}}",
  "isActive": true,
  "priority": 100
}
```

**成功响应（201）**：`{"code": 201, "message": "success"}`

**错误响应**：
- 厂商类型已存在（409）：`{"code": 10009, "message": "厂商类型已存在"}`

#### 7.5.3 更新厂商

**接口**：`PATCH /api/v1/admin/providers/{id}`

**功能描述**：部分更新厂商字段，变更后双删缓存。

**请求体**（示例）：
```json
{
  "providerName": "OpenAI Updated",
  "priority": 80,
  "isActive": false
}
```

**成功响应（200）**：`{"code": 200, "message": "success"}`

#### 7.5.4 删除厂商

**接口**：`DELETE /api/v1/admin/providers/{id}`

**成功响应（200）**：`{"code": 200, "message": "success"}`

#### 7.5.5 启用/禁用厂商

**接口**：`PATCH /api/v1/admin/providers/{id}/active?isActive=false`

**查询参数**：
- `isActive`：`true` 启用，`false` 禁用

**成功响应（200）**：`{"code": 200, "message": "success"}`

### 7.6 LLM 配置接口

#### 7.4.1 获取用户 LLM 配置列表

**接口**：`GET /api/v1/llm/configs`

**功能描述**：获取当前用户配置的所有 LLM API 信息，包含已禁用的配置。

**请求头**：`Authorization: Bearer {token}`

**查询参数**：
- `providerType`（可选）：按厂商类型过滤，如 `openai`
- `isActive`（可选）：按启用状态过滤，`true` 或 `false`
- `capability`（可选）：按能力过滤，如 `CHAT`、`OCR`

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "uuid-xxx",
        "configName": "我的 GPT-4",
        "providerType": "openai",
        "providerName": "OpenAI",
        "modelName": "gpt-4",
        "capabilities": ["CHAT", "OCR"],
        "apiKeyMasked": "sk-****....****",
        "customApiBaseUrl": null,
        "priority": 100,
        "isActive": true,
        "isDefault": true,
        "timeoutMs": 60000,
        "maxRetries": 3,
        "streamEnabled": true,
        "extraConfig": {"temperature": 0.7},
        "createdAt": "2026-04-10 12:00:00",
        "updatedAt": "2026-04-10 12:00:00"
      }
    ]
  }
}
```

**注意**：`apiKeyMasked` 字段仅展示脱敏后的 Key，格式为 `sk-****....****`，实际 Key 不返回前端。

#### 7.4.2 创建用户 LLM 配置

**接口**：`POST /api/v1/llm/configs`

**功能描述**：新增一个 LLM API 配置，API Key 会自动加密存储。

**请求头**：`Authorization: Bearer {token}`

**请求体**：
```json
{
  "providerType": "openai",
  "configName": "我的 GPT-4",
  "apiKey": "sk-xxxxxxxxxxxxxxxx",
  "modelName": "gpt-4",
  "priority": 100,
  "isDefault": true,
  "timeoutMs": 60000,
  "maxRetries": 3,
  "streamEnabled": true,
  "extraConfig": {
    "temperature": 0.7
  }
}
```

**成功响应（201）**：
```json
{
  "code": 201,
  "message": "success",
  "data": {
    "id": "uuid-xxx",
    "configName": "我的 GPT-4",
    "providerType": "openai",
    "apiKeyMasked": "sk-****....****"
  }
}
```

#### 7.4.3 更新用户 LLM 配置

**接口**：`PATCH /api/v1/llm/configs/{id}`

**功能描述**：部分更新配置字段，仅传入需要修改的字段。支持修改 API Key（会重新加密）。

**请求头**：`Authorization: Bearer {token}`

**请求体**（示例，包含多个可更新字段）：
```json
{
  "apiKey": "sk-yyyyyyyyyyyyyyyy",
  "priority": 80,
  "isActive": false,
  "isDefault": true,
  "extraConfig": {
    "temperature": 0.9
  }
}
```

**成功响应（200）**：`{"code": 200, "message": "success"}`

**说明**：
- 如果 `isDefault` 设置为 `true`，会自动将其他配置的 `isDefault` 设为 `false`
- 更新后会自动失效 Redis 缓存，下次访问时重新加载

#### 7.4.4 删除用户 LLM 配置

**接口**：`DELETE /api/v1/llm/configs/{id}`

**功能描述**：删除指定的 LLM 配置。删除后不会立即删除历史对话消息，但会影响新对话的使用。

**请求头**：`Authorization: Bearer {token}`

**成功响应**：204 No Content

### 7.7 对话接口

#### 7.7.1 创建对话

**接口**：`POST /api/v1/chat/conversations`

**功能描述**：创建一个新的对话会话，可指定初始使用的 LLM 配置。

**请求头**：`Authorization: Bearer {token}`

**请求体**：
```json
{
  "title": "关于 RAG 的讨论",
  "lastConfigId": "uuid-xxx"
}
```

- `title`（可选）：对话标题，不提供则使用默认标题
- `lastConfigId`（可选）：默认使用的 LLM 配置 ID

**成功响应（201）**：
```json
{
  "code": 201,
  "message": "success",
  "data": {
    "id": "uuid-new",
    "title": "关于 RAG 的讨论",
    "lastConfigId": "uuid-xxx",
    "lastModelName": "gpt-4",
    "isPinned": false,
    "createdAt": "2026-04-12 10:00:00"
  }
}
```

#### 7.7.2 获取对话列表

**接口**：`GET /api/v1/chat/conversations`

**功能描述**：获取当前用户的所有对话列表，按更新时间倒序排列，支持分页。

**请求头**：`Authorization: Bearer {token}`

**查询参数**：
- `page`（可选，默认 1）：页码
- `pageSize`（可选，默认 20）：每页条数

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "uuid-xxx",
        "title": "关于 RAG 的讨论",
        "lastModelName": "gpt-4",
        "isPinned": true,
        "updatedAt": "2026-04-12 10:30:00"
      }
    ],
    "total": 42,
    "page": 1,
    "pageSize": 20
  }
}
```

**说明**：列表中不包含已删除的对话（软删除）。

#### 7.7.3 获取对话历史消息

**接口**：`GET /api/v1/chat/conversations/{id}/messages`

**功能描述**：获取指定对话的所有消息，按创建时间正序排列。

**请求头**：`Authorization: Bearer {token}`

**查询参数**：
- `page`（可选，默认 1）：页码
- `pageSize`（可选，默认 50）：每页条数

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "uuid-msg-1",
        "role": "user",
        "content": "什么是 RAG？",
        "modelName": null,
        "tokenCount": 0,
        "createdAt": "2026-04-12 10:00:00"
      },
      {
        "id": "uuid-msg-2",
        "role": "assistant",
        "content": "RAG 是检索增强生成...",
        "modelName": "gpt-4",
        "tokenCount": 312,
        "createdAt": "2026-04-12 10:00:05"
      }
    ],
    "total": 6,
    "page": 1,
    "pageSize": 50
  }
}
```

**说明**：
- `tokenCount` 对于用户消息为 0
- `modelName` 对于用户消息为 null

#### 7.7.4 删除对话

**接口**：`DELETE /api/v1/chat/conversations/{id}`

**功能描述**：软删除对话，标记 `is_deleted = true`，对话内容保留但列表中不显示。

**请求头**：`Authorization: Bearer {token}`

**成功响应**：204 No Content

### 7.8 用量统计接口

#### 7.8.1 获取用量汇总

**接口**：`GET /api/v1/llm/usage/summary`

**功能描述**：获取指定时间范围内的用量汇总数据。

**请求头**：`Authorization: Bearer {token}`

**查询参数**：
- `startDate`（必填）：开始日期，格式 `yyyy-MM-dd`
- `endDate`（必填）：结束日期，格式 `yyyy-MM-dd`

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalCalls": 100,
    "totalTokens": 50000,
    "promptTokens": 30000,
    "completionTokens": 20000,
    "averageLatencyMs": 1500
  }
}
```

#### 7.8.2 获取日度用量

**接口**：`GET /api/v1/llm/usage/daily`

**功能描述**：按天统计用量数据，便于查看使用趋势。

**请求头**：`Authorization: Bearer {token}`

**查询参数**：
- `startDate`（必填）：开始日期
- `endDate`（必填）：结束日期

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "date": "2026-04-01",
      "calls": 50,
      "promptTokens": 15000,
      "completionTokens": 10000,
      "totalTokens": 25000
    },
    {
      "date": "2026-04-02",
      "calls": 50,
      "promptTokens": 15000,
      "completionTokens": 10000,
      "totalTokens": 25000
    }
  ]
}
```

#### 7.8.3 获取用量明细

**接口**：`GET /api/v1/llm/usage/logs`

**功能描述**：获取每次 LLM 调用的详细记录。

**请求头**：`Authorization: Bearer {token}`

**查询参数**：
- `startDate`（必填）：开始日期
- `endDate`（必填）：结束日期
- `page`（可选，默认 1）：页码
- `pageSize`（可选，默认 20）：每页条数

**成功响应（200）**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "uuid-xxx",
        "configId": "user-config-uuid",
        "providerType": "openai",
        "modelName": "gpt-4",
        "promptTokens": 20,
        "completionTokens": 150,
        "totalTokens": 170,
        "latencyMs": 1234,
        "status": "success",
        "createdAt": "2026-04-10 12:00:00"
      }
    ],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

---

## 八、错误码设计

### 8.1 错误码分类

| 错误码范围 | 分类 | 说明 |
|-----------|------|------|
| 10001-19999 | LLM 配置相关 | 厂商配置、用户配置相关错误 |
| 20001-29999 | 用户/认证相关 | 用户信息、登录认证、对话相关错误 |
| 50001-59999 | 系统错误 | 未知错误、系统异常 |

### 8.2 错误码详细说明

| 错误码 | 枚举名 | 说明 | HTTP 状态 |
|--------|--------|------|----------|
| 10001 | PROVIDER_NOT_FOUND | 系统厂商不存在 | 404 |
| 10002 | PROVIDER_DISABLED | 系统厂商已被禁用 | 400 |
| 10003 | PROVIDER_IN_USE | 厂商被用户使用中，无法删除 | 409 |
| 10004 | USER_CONFIG_NOT_FOUND | 用户配置不存在 | 404 |
| 10005 | USER_CONFIG_DISABLED | 用户配置已被禁用 | 400 |
| 10006 | NO_DEFAULT_CONFIG | 用户没有设置默认配置 | 404 |
| 10007 | INVALID_API_KEY | API Key 格式无效 | 400 |
| 10008 | MODEL_NOT_SUPPORTED | 模型不被该厂商支持 | 400 |
| 10009 | DUPLICATE_USER_CONFIG | 用户已存在该厂商相同模型的配置 | 409 |
| 20001 | USER_NOT_FOUND | 用户不存在 | 404 |
| 20002 | INVALID_PASSWORD | 密码错误 | 401 |
| 20003 | AUTH_DISABLED | 账号已被禁用 | 403 |
| 20004 | CONVERSATION_NOT_FOUND | 对话不存在 | 404 |
| 20005 | UNAUTHORIZED_ACCESS | 无权访问该对话内容 | 403 |
| 50001 | UNKNOWN_ERROR | 系统内部错误 | 500 |

### 8.3 错误响应格式

所有错误响应均使用统一格式：

```json
{
  "code": 10004,
  "message": "用户配置不存在",
  "detail": "配置ID: uuid-xxx 不存在于数据库",
  "timestamp": "2026-04-12 10:00:00",
  "path": "/api/v1/llm/configs/uuid-xxx",
  "requestId": "uuid-trace-id"
}
```

**字段说明**：
- `code`：错误码，对应上表中的数值
- `message`：简短错误消息
- `detail`：详细说明，包含具体参数或上下文
- `timestamp`：服务器时间
- `path`：请求路径
- `requestId`：请求追踪 ID，便于日志定位

---

## 九、缓存策略

### 9.1 缓存场景

系统中对实时性要求不高的数据会缓存到 Redis 中，以减少数据库压力并提升读取性能。

**缓存数据类型**：
1. 用户 LLM 配置（高频读取，变更不频繁）
2. 系统厂商信息（管理员修改，用户只读）
3. 用户默认配置 ID（高频读取）

### 9.2 缓存 Key 规范

| 数据类型 | Key 格式 | TTL | 说明 |
|---------|---------|-----|------|
| 用户信息 | `user:info:{userId}` | 7 天 | 用户完整信息，登录时写入 |
| 用户 LLM 配置 | `llm:cfg:{configId}` | 7 天 | 配置详情缓存 |
| 系统厂商信息 | `llm:pvd:{providerType}` | 30 天 | 厂商元数据缓存 |
| 用户默认配置 ID | `llm:u_def:{userId}` | 30 天 | 快速获取用户默认配置 |

### 9.3 缓存更新策略：双删延迟

当配置数据发生变更时（更新或删除），为保证缓存与数据库的一致性，采用双删延迟策略。

**为什么需要双删**：
- 如果只有第一删：可能出现"第一删之后、第二删之前"这段时间内，有新请求将旧数据回填到缓存
- 双删确保即使发生回填，也会在 1 秒后被清除，下次访问会加载最新数据

#### 9.3.1 实现代码

**DoubleDeleteCacheService.java**：

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class DoubleDeleteCacheService {

    private static final int FIRST_DELETE_MAX_RETRIES = 3;
    private static final long FIRST_DELETE_RETRY_INTERVAL_MS = 200;
    private static final long SECOND_DELETE_DELAY_MS = 1000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ThreadPoolTaskScheduler taskScheduler;

    /**
     * 驱逐配置缓存（双删策略）
     */
    public void evictConfigCache(String configId) {
        String cacheKey = buildConfigCacheKey(configId);

        // 1. 第一删（同步，带重试）
        boolean firstDeleteSuccess = deleteCacheWithRetry(cacheKey);
        if (!firstDeleteSuccess) {
            log.warn("第一删失败，依赖第二删兜底: {}", cacheKey);
        }

        // 2. 延迟第二删（1秒后）
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 驱逐用户默认配置缓存
     */
    public void evictDefaultConfigCache(String userId) {
        String cacheKey = "llm:u_def:" + userId;
        deleteCacheWithRetry(cacheKey);
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 驱逐系统厂商缓存
     */
    public void evictProviderCache(String providerType) {
        String cacheKey = "llm:pvd:" + providerType;
        deleteCacheWithRetry(cacheKey);
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 驱逐用户信息缓存
     */
    public void evictUserInfoCache(String userId) {
        String cacheKey = "user:info:" + userId;
        deleteCacheWithRetry(cacheKey);
        scheduleSecondDelete(cacheKey);
    }

    /**
     * 驱逐用户角色缓存（已由 evictUserInfoCache 统一处理，保留以备扩展）
     */
    public void evictUserRoleCache(String userId) {
        evictUserInfoCache(userId);
    }

    /**
     * 带重试的缓存删除（第一删）
     */
    public boolean deleteCacheWithRetry(String cacheKey) {
        for (int i = 0; i < FIRST_DELETE_MAX_RETRIES; i++) {
            try {
                if (Boolean.TRUE.equals(redisTemplate.delete(cacheKey))) {
                    log.debug("第一删成功: {}", cacheKey);
                    return true;
                }
                // key 不存在也算删除成功
                return true;
            } catch (Exception e) {
                log.warn("第一删第{}次失败: {}, error: {}", i + 1, cacheKey, e.getMessage());
                if (i < FIRST_DELETE_MAX_RETRIES - 1) {
                    sleepSilently(FIRST_DELETE_RETRY_INTERVAL_MS);
                }
            }
        }
        return false;
    }

    /**
     * 延迟第二删
     */
    private void scheduleSecondDelete(String cacheKey) {
        taskScheduler.schedule(() -> {
            try {
                deleteCache(cacheKey);
                log.debug("第二次删除缓存: {}", cacheKey);
            } catch (Exception e) {
                log.warn("第二次删除失败: {}", cacheKey, e);
            }
        }, new Date(System.currentTimeMillis() + SECOND_DELETE_DELAY_MS));
    }

    private void deleteCache(String cacheKey) {
        redisTemplate.delete(cacheKey);
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildConfigCacheKey(String configId) {
        return "llm:cfg:" + configId;
    }
}
```

#### 9.3.2 调用时机

| 场景 | 调用方法 |
|------|---------|
| 用户登录/注册 | `UserCacheService.put(userId, dto)` |
| 修改个人资料 | `UserCacheService.evict(userId)` |
| 修改用户状态/角色（管理员） | `UserCacheService.evict(userId)` |
| 更新用户 LLM 配置 | `evictConfigCache(configId)` |
| 删除用户 LLM 配置 | `evictConfigCache(configId)` |
| 修改用户默认配置 | `evictDefaultConfigCache(userId)` |
| 创建/更新/删除系统厂商 | `evictProviderCache(providerType)` |

#### 9.3.3 参数说明

| 参数 | 值 | 说明 |
|------|-----|------|
| FIRST_DELETE_MAX_RETRIES | 3 | 第一删最大重试次数 |
| FIRST_DELETE_RETRY_INTERVAL_MS | 200 | 每次重试间隔（毫秒） |
| SECOND_DELETE_DELAY_MS | 1000 | 第二删延迟时间（毫秒） |

### 9.4 缓存故障处理

当 Redis 出现故障时，系统有以下降级策略：

1. **缓存穿透**：缓存未命中时直接查询数据库
2. **缓存雪崩**：通过随机 TTL 避免大量 Key 同时过期
3. **永久故障**：旧缓存存活到 TTL 后自然过期，不阻塞业务

---

## 十、部署架构

### 10.1 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (Web)                            │
│                    浏览器或移动端应用                          │
└─────────────────────────────┬───────────────────────────────┘
                              │ HTTP/HTTPS
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Java 管理端 (ToLink Service)                │
│                        端口：8080                             │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   Controller │  │   Service   │  │   Mapper    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐                          │
│  │   认证鉴权   │  │   异常处理   │                          │
│  │ (sa-token)  │  │ (全局处理)  │                          │
│  └─────────────┘  └─────────────┘                          │
└───────────────────────┬─────────────────┬───────────────────┘
                        │                 │
                        ▼                 ▼
              ┌─────────────────┐  ┌───────────────┐
              │     MySQL       │  │     Redis     │
              │  36.213.180.176 │  │   本地/远端    │
              │   tolink_rag_db │  │               │
              └────────┬────────┘  └───────┬───────┘
                       │                    │
                       └─────────┬──────────┘
                                 │
                                 ▼
              ┌─────────────────────────────────────┐
              │       Python RAG 执行端              │
              │           端口：8000                 │
              │                                     │
              │  · 读取 Redis 缓存的 LLM 配置         │
              │  · 调用各厂商 LLM API               │
              │  · 执行 RAG 检索和生成              │
              │  · 写入消息和用量日志到 MySQL        │
              └─────────────────────────────────────┘
```

### 10.2 环境说明

| 环境 | 部署方式 | MySQL | Redis |
|------|---------|-------|-------|
| 开发环境 | 本地运行 | localhost:3306 | localhost:6379 |
| 生产环境 | 云服务器 | 36.213.180.176:3306 | 远端或本地 |

### 10.3 数据流向

**写入操作**（Java 管理端 → MySQL）：
```
用户请求 → Controller → Service → Mapper → MySQL
                                     ↓
                              删除 Redis 缓存
```

**读取操作**（Python 执行端 → Redis/MySQL）：
```
请求配置 → 查 Redis → 命中？→ 是 → 返回
                    ↓ 否
                  查 MySQL → 回填 Redis → 返回
```

**用量记录**（Python 执行端 → MySQL）：
```
LLM 调用完成 → 写入 llm_usage_log → Java 端可查询
```

---

## 十一、初始数据

### 11.1 管理员账户

系统初始化时自动创建管理员账户：

| 字段 | 值 |
|------|-----|
| 用户名 | admin |
| 密码 | admin123 |
| 角色 | ADMIN |
| 密码存储 | BCrypt 哈希 |

**安全提示**：生产环境部署后，请立即修改默认密码。

### 11.2 LLM 厂商配置

系统预置了四个主流 LLM 厂商配置：

| 厂商名称 | 类型标识 | API 地址 | 优先级 |
|---------|---------|---------|-------|
| OpenAI | openai | https://api.openai.com/v1 | 100 |
| Anthropic Claude | claude | https://api.anthropic.com/v1 | 90 |
| 智谱 AI | glm | https://open.bigmodel.cn/api/paas/v4 | 80 |
| DeepSeek | deepseek | https://api.deepseek.com/v1 | 70 |

---

## 十二、运行指南

### 12.1 环境要求

- **Java**：17 或更高版本
- **Maven**：3.6 或更高版本
- **MySQL**：8.0 或更高版本
- **Redis**：7.x 或更高版本

### 12.2 配置文件

项目使用 `link-service/src/main/resources/application.yml` 管理所有配置，数据库、Redis 和 LLM 加密密钥已直接写入：

```yaml
spring:
  datasource:
    url: jdbc:mysql://36.213.180.176:3306/tolink_rag_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: ql354210

  redis:
    host: 36.213.180.176
    port: 6379
    username: root
    password: ql354210

llm:
  api-key:
    secret: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

> 如需修改连接信息，直接编辑 `application.yml` 中的对应配置项。

### 12.3 数据库初始化

首次部署时，需要执行数据库初始化脚本：

```bash
mysql -h 36.213.180.176 -u root -p < link-service/src/main/resources/schema.sql
```

初始化内容包括：
- 创建数据库（如果不存在）
- 创建所有数据表
- 插入初始管理员账户
- 插入 LLM 厂商配置

### 12.4 启动服务

配置文件已就绪，无需额外设置环境变量，直接启动：

```bash
# 进入项目目录
cd /Users/fang/Developer/Projects/toLink-Service

# 启动服务
mvn spring-boot:run -pl link-service
```

### 12.5 验证服务

服务启动后，可通过以下方式验证：

**检查健康状态**：
```bash
curl http://localhost:8080/actuator/health
```

**测试登录接口**：
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 12.6 接口文档

启动服务后，可访问 Swagger UI 查看完整 API 文档：

```
http://localhost:8080/swagger-ui.html
```
