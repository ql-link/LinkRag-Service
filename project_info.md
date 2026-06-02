# ToLink Service 项目现状

## 1. 项目定位

`toLink-Service` 是 ToLink 的 Java 管理端服务，面向用户管理、LLM 配置管理、数据集管理、知识文件上传、解析任务投递、解析结果回传与前端查询。

Python RAG 执行端负责文档解析、RAG 执行、LLM 调用和解析产物生成；Java 端负责管理入口、元数据、权限、文件入口、MQ 协作和用户可见状态。

## 2. 技术栈

| 类别 | 当前实现 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 2.5.3 |
| 构建 | Maven 多模块 |
| ORM | MyBatis-Plus |
| 鉴权 | sa-token |
| 数据库 | MySQL，数据库名 `tolink_rag_db` |
| 缓存 | Redis |
| MQ | Kafka / RabbitMQ 组件抽象，当前配置默认 Kafka |
| OSS | 本地存储 / MinIO |
| 测试 | JUnit、Mockito、SpringBootTest、MockMvc |

## 3. 模块地图

| 模块 | 职责 |
| --- | --- |
| `link-model` | Entity、Request/Response DTO、枚举、统一响应 |
| `link-core` | 异常、全局异常处理、认证上下文、加密、线程池与基础配置 |
| `link-components` | Redis、MQ、OSS 横向组件 |
| `link-mapper` | MyBatis-Plus Mapper |
| `link-service` | 用户、配置、数据集、知识文件、解析、用量等业务服务 |
| `link-api` | Controller、启动类、接口层测试 |

## 4. 当前业务域

- 用户认证、用户资料、管理员用户管理
- 系统 LLM 厂商与用户 LLM 配置
- 对话、消息、用量统计
- 数据集与知识文件管理
- 原始文件上传、私有文件读取、解析任务提交
- Java/Python 解析链路协作：MQ 投递、结果回传、SSE 事件推送
- Redis 缓存一致性和缓存补偿
- OSS 本地/MinIO 文件存储

## 5. 关键协作链路

### 5.1 文件解析链路

1. 前端通过 Java 上传知识文件。
2. Java 写入文件元数据、OSS 对象信息和解析聚合记录。
3. 用户触发解析后，Java 在事务内更新 `latest_parse_task_id` 并发送 `tolink.rag.parse_task`。
4. Python 消费解析任务，读取 Java 暴露的内部文件内容接口。
5. Python 推进解析并通过 `tolink.rag.parse_result` 回传终态结果。
6. Java 消费结果消息，校验 Python 已持久化的解析日志与归属关系，并通过 SSE 转发前端事件。

### 5.2 缓存链路

- 用户信息、LLM 配置、默认配置映射、知识文件运行配置使用 Redis。
- 写路径通过 `CacheConsistencyService` 同步删缓存。
- 缓存补偿通过 `tolink.cache.evict` 主题消费。

### 5.3 OSS 链路

- `IOssService` 抽象本地/MinIO 文件操作。
- private 文件通过 `PrivateFileResolver` 支持本地缓存读取。
- public 文件可通过本地预览 Controller 读取。

## 6. AI 工作体系现状

项目已切换为 Spec-as-Test：

```text
brief.md -> acceptance.feature -> technical_design.md -> Code + Tests
```

- `.ai/` 是 AI 资产唯一物理来源。
- `AGENTS.md`、`CLAUDE.md`、`.claude/skills`、`.agent/skills` 通过 symlink 指向 `.ai`。
- 新需求默认写入 `.specs/<需求名>/`。
- 旧七阶段模块文档已移除，新需求统一进入 `.specs/<需求名>/`。

## 7. 推荐阅读顺序

1. `AGENTS.md` 或 `CLAUDE.md`
2. `docs/contributing.md`
3. `docs/internals/project_structure.md`
4. `docs/api/api_contracts.md`
5. 与任务相关的 internals/api/ops 文档
