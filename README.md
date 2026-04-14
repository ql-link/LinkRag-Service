# ToLink Service

多模块 Maven 项目，提供 AI LLM 代理与管理服务。

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 2.5.3 | 核心框架 |
| MyBatis-Plus | 3.4.2 | ORM 框架 |
| sa-token | 1.39.0 | 认证框架 |
| MySQL | 8.0 | 主数据库 |
| Redis | 7.x | 缓存 + Token 存储 |

## 模块架构

```
link-model       # 数据模型层：Entity、Enum、Request/Response DTO
link-core        # 核心层：异常体系、工具类（ApiKeyEncryptService、AuthContext）
link-components  # 通用组件中台：Redis 组件（含双删缓存）、MQ、OSS 等
link-mapper      # 数据访问层：MyBatis-Plus Mapper 接口
link-api         # Controller 层 + Spring Boot 启动类
link-service     # Service 层：核心业务逻辑
```

**模块依赖方向**：
```
link-model ←── link-core ←── link-api ←── link-service
                 ↑              ↑
                 │               └──► link-mapper ←── link-model
                 │
link-components
```

## 主要功能

- **用户管理**：注册、登录、信息维护（BCrypt 密码加密）
- **LLM 厂商配置**：系统级厂商管理 + 用户级 API Key 配置（AES-256-GCM 加密存储）
- **对话管理**：创建、查询、软删除
- **用量统计**：汇总、日度统计、明细查询
- **RBAC 权限**：ADMIN / USER 两种角色

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` | localhost | MySQL 主机 |
| `DB_PORT` | 3306 | MySQL 端口 |
| `DB_USERNAME` | root | 数据库用户名 |
| `DB_PASSWORD` | - | 数据库密码（必须配置） |
| `REDIS_HOST` | localhost | Redis 主机 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_PASSWORD` | - | Redis 密码 |
| `LLM_SECRET` | - | AES-256-GCM 密钥（64位十六进制） |

## 快速开始

### 1. 初始化数据库

```bash
mysql -h <DB_HOST> -u root -p < docs/db/schema.sql
mysql -h <DB_HOST> -u root -p tolink_rag_db < docs/db/init.sql
```

### 2. 配置环境变量或修改 application.yml

```yaml
# link-service/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:3306/tolink_rag_db
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}

llm:
  api-key:
    secret: ${LLM_SECRET:}  # 64位十六进制字符串
```

### 3. 启动服务

```bash
mvn spring-boot:run -pl link-api
```

服务启动后访问：`http://localhost:8080/swagger-ui.html`

### 4. 默认账户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN |

## 项目结构

```
tolink-service/
├── docs/
│   ├── ToLink-Service设计文档.md    # 完整技术设计文档
│   └── db/
│       ├── schema.sql              # 数据库建表脚本
│       └── init.sql                # 初始化数据脚本
├── link-model/                    # 数据模型层
│   └── src/main/java/com/qingluo/link/model/
│       ├── dto/                   # entity/request/response
│       └── enums/
├── link-core/                     # 核心层
│   └── src/main/java/com/qingluo/link/core/
│       ├── exception/             # 异常体系
│       └── util/                  # 工具类
├── link-components/              # 通用组件中台
│   └── toLink-components-redis/   # Redis 组件（双删缓存）
├── link-mapper/                   # 数据访问层
├── link-api/                      # Controller 层
│   └── src/main/java/com/qingluo/link/api/
│       ├── controller/            # HTTP 接口
│       └── stp/                   # sa-token RBAC 集成
└── link-service/                  # Service 层
    └── src/main/java/com/qingluo/link/service/
        └── impl/                 # 业务实现
```

## API 概览

| 模块 | 接口 | 说明 |
|------|------|------|
| 认证 | `POST /api/v1/auth/login` | 用户登录 |
| 认证 | `POST /api/v1/auth/register` | 用户注册 |
| 认证 | `POST /api/v1/auth/logout` | 退出登录 |
| 用户 | `GET /api/v1/user/profile` | 获取用户信息 |
| 用户 | `PATCH /api/v1/user/profile` | 修改个人资料 |
| 管理 | `GET /api/v1/admin/users` | 用户列表（ADMIN） |
| 管理 | `PATCH /api/v1/admin/users/{id}/status` | 启用/禁用用户（ADMIN） |
| 管理 | `PATCH /api/v1/admin/users/{id}/role` | 修改用户角色（ADMIN） |
| 管理 | `GET /api/v1/admin/providers` | 厂商列表（ADMIN） |
| 管理 | `POST /api/v1/admin/providers` | 创建厂商（ADMIN） |
| LLM | `GET /api/v1/llm/configs` | 获取用户 LLM 配置 |
| LLM | `POST /api/v1/llm/configs` | 创建 LLM 配置 |
| LLM | `PATCH /api/v1/llm/configs/{id}` | 更新 LLM 配置 |
| LLM | `DELETE /api/v1/llm/configs/{id}` | 删除 LLM 配置 |
| 对话 | `POST /api/v1/chat/conversations` | 创建对话 |
| 对话 | `GET /api/v1/chat/conversations` | 对话列表 |
| 对话 | `GET /api/v1/chat/conversations/{id}/messages` | 消息历史 |
| 对话 | `DELETE /api/v1/chat/conversations/{id}` | 删除对话 |
| 用量 | `GET /api/v1/llm/usage/summary` | 用量汇总 |
| 用量 | `GET /api/v1/llm/usage/daily` | 日度用量 |
| 用量 | `GET /api/v1/llm/usage/logs` | 用量明细 |

## 安全说明

- **密码存储**：BCrypt 单向哈希，不可逆
- **API Key 加密**：AES-256-GCM 对称加密，IV 随机生成
- **认证方式**：sa-token Header 模式，Token 存储于 Redis，7 天有效期
- **权限控制**：`@SaCheckRole("ADMIN")` 注解保护管理员接口

## 数据库

- 数据库名：`tolink_rag_db`
- 字符集：UTF-8 MB4
- 主键策略：`assign_id`（雪花算法）
- 逻辑删除：MyBatis-Plus 自动处理 `is_deleted` 字段

## 缓存策略

采用双删延迟策略保证缓存一致性：

| 缓存类型 | Key 格式 | TTL |
|---------|---------|-----|
| 用户信息 | `user:info:{userId}` | 7 天 |
| 用户 LLM 配置 | `llm:cfg:{configId}` | 7 天 |
| 系统厂商信息 | `llm:pvd:{providerType}` | 30 天 |
| 用户默认配置 ID | `llm:u_def:{userId}` | 30 天 |

## License

Private Project
