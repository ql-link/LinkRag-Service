<div align="center">

# LinkRag-Service

LinkRag 的 Java 管理端——把用户、配置、文件与任务编排稳稳托在控制面，让 Python 专注 RAG。

</div>

<p align="center">
  <b>简体中文</b> · <a href="./README_EN.md">English</a>
</p>

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-2.5.3-6DB33F?logo=springboot&logoColor=white">
  <img alt="MyBatis-Plus" src="https://img.shields.io/badge/MyBatis--Plus-ORM-red">
  <img alt="Sa-Token" src="https://img.shields.io/badge/Sa--Token-Auth-1E90FF">
  <img alt="MySQL" src="https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white">
  <img alt="Redis" src="https://img.shields.io/badge/Redis-Cache-DC382D?logo=redis&logoColor=white">
  <img alt="Kafka" src="https://img.shields.io/badge/Kafka-MQ-231F20?logo=apachekafka&logoColor=white">
  <img alt="MinIO" src="https://img.shields.io/badge/MinIO-OSS-C72E49?logo=minio&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-blue">
</p>

<p align="center">
  <a href="http://linkrag.cn/"><img alt="在线体验 linkrag.cn" src="https://img.shields.io/badge/在线体验-linkrag.cn-c8a06a?style=for-the-badge&logo=googlechrome&logoColor=white"></a>
</p>

<p align="center">
  <img alt="LinkRag 总体架构：两个大脑隔河协作，四条共享通道传递" src="./docs/assets/sketches/sketch-architecture.png" width="840">
</p>

## LinkRag-Service 是什么？

`LinkRag-Service` 是 **LinkRag 系统的 Java 管理端**。LinkRag 是一套人人可用的企业级 RAG 系统，让任何人都能把复杂文档变成可对话的知识库——而把文档解析、分片、向量化、检索到问答生成的全链路重活，交给 Python RAG 服务（[ql-link/LinkRag](https://github.com/ql-link/LinkRag)）去做。

本仓是这套系统的**控制面**：用户与权限、LLM 配置、对话与用量、数据集与知识文件、对象存储入口、解析任务投递与结果查询、缓存一致性、召回令牌签发——所有"谁能用、配置如何、文件在哪、进展怎样"的事都在这里收口，让 Python 端专注"怎么算"。

整套系统是「**Java 管理端 + Python 执行端**」的双脑协作：两端从不直接相互调用，全靠四条共享通道——MySQL 共享库、消息队列、Redis 缓存、OSS/MinIO 文件——异步解耦。Java 下发解析任务、Python 执行后把终态写回共享库，前端经 Java 轮询读取；唯一的实时例外是对话流——前端凭 Java 签发的短时令牌直连 Python 拉取答案。系统边界与模块细节见 [docs/internals/project_structure.md](docs/internals/project_structure.md)。

## 主要工程亮点

控制面看似只是"增删改查 + 转发"，但要在高并发、跨进程、易抖动的真实环境里把状态管对，处处都是工程取舍。下面六项是 LinkRag-Service 区别于普通 CRUD 后端的地方。

**1. 上传即响应，文件在后台稳稳落库——文档上传异步 Pipeline**

大文件上传，用户最怕点完提交后干等进度条，连接一断前功尽弃。LinkRag-Service 把"落库"和"传文件"拆开：请求线程只写元数据、立刻返回，真正的对象存储推送交给后台线程池在事务提交后异步完成，全程不占用数据库事务。三条线——后台上传、队列满拒绝、定时巡逻——最终都汇向同一道"状态守卫"写入，每次更新都带「仅当状态仍是上传中」的条件，谁先到谁赢、晚到者自动空转，天然幂等；进程重启遗留的在途任务，由每分钟一次的巡逻扫描兜底改判失败，让用户可重试。

<p align="center">
  <img alt="文档上传异步 Pipeline：三条线汇入同一道状态守卫" src="./docs/assets/sketches/sketch-document-upload.png" width="680">
</p>

**2. 三类缓存病一次防住，缓存与库最终一致——读保护与变更补偿**

高并发下缓存最容易出三种事故：查不到的数据反复打到库（穿透）、热点失效瞬间并发回源（击穿）、大批 key 同时过期压垮库（雪崩）；更新数据后还可能读到旧缓存。LinkRag-Service 用一套统一入口同时治三病——空结果写"占位标记"挡穿透、单 key 锁只放一个线程回源挡击穿、写入加随机过期抖动挡雪崩。写路径双轨删除：业务更新成功后在事务提交后删一次，再订阅数据库 binlog 经 CDC 桥接补删一次，两条路都走同一个"缓存键翻译员"，键名口径永远一致，任何一条漏网都有第二道补偿收敛。

<p align="center">
  <img alt="读保护与变更补偿：两条河流汇入同一个缓存键翻译员" src="./docs/assets/sketches/sketch-cache.png" width="680">
</p>

**3. 一轮对话稳稳落一行，账单不重不漏——对话轮次可靠落库**

AI 对话是异步流式的，Python 一轮会发多条状态消息（开始生成、生成完毕 / 失败），跨进程、可能乱序、可能重投——稍不小心就会写重、算重账，或把已完成的记录覆盖回"生成中"。LinkRag-Service 以前端每轮生成的稳定标识为幂等键，把多条消息合并进同一行；状态机只进不退，终态记录拒绝被任何迟到消息改写；用量账单只在进入终态时写一次，"生成中"阶段不计费。落库前强制校验对话归属，杜绝跨用户写入。消息无论怎么乱序重投，数据库里始终是干净的一行。

<p align="center">
  <img alt="对话轮次可靠落库：两条通知合并成数据库里的一行，状态只进不退" src="./docs/assets/sketches/sketch-chat-turn.png" width="680">
</p>

**4. 控制面只发通行证，不扛对话流量——召回通行证签发**

对话召回是高频、长连接、大流量的 SSE 流，如果让 Java 管理端做中转代理，控制面会被长连接拖垮。LinkRag-Service 退到"门卫"角色：校验登录态、账号状态、数据集归属后，用独立密钥签一张短时效令牌，把已授权的知识库范围写死进令牌；前端凭票直接连 Python 拉召回 / 生成流，Java 完全不在这条数据路径上，资源滥用由 Python 按用户并发上限兜底。控制面只做轻量鉴权与签发，把大流量长连接的压力留给专门的执行端。

<p align="center">
  <img alt="召回通行证签发：门卫签票，前端凭票直入 Python 执行室" src="./docs/assets/sketches/sketch-recall-session.png" width="680">
</p>

**5. 解析失败从断点续跑，不必从头再来——解析任务与重试链**

一篇文档解析要过多道工序，任一步因外部服务抖动失败都可能让用户白等；重复提交、并发重投又会把任务搞乱。LinkRag-Service 给每个文件维护一根"当前任务指针"，提交时读「指针 → 解析日志 → 流水线状态」三层来判定首次 / 重试 / 进行中 / 已成功——进行中和已成功直接拦截，避免重复投递。失败重试时复用上一轮已产出的中间文件地址、带上一轮任务编号，让 Python 跳过已完成阶段、从断点续跑；终态判定只认流水线状态这一权威源，不被主从延迟误导。

<p align="center">
  <img alt="解析任务与重试链：一根移动的任务指针，失败从中间文件续跑" src="./docs/assets/sketches/sketch-parse-retry.png" width="680">
</p>

**6. 加一种消息只写一个类，拓扑自动注册——消息队列抽象框架**

消息系统一多，手工维护 topic 声明、绑定关系既繁琐又容易漏配；换 MQ 厂商更是牵一发动全身。LinkRag-Service 让每种消息只实现一个契约接口、回答三件事——队列叫什么、用队列还是广播、内容怎么序列化；应用启动时自动扫描所有实现类，把拓扑批量注册进 Kafka，零手工配置文件。发送和消费各有统一门面，Kafka 与 RabbitMQ 实现同一套接口，换厂商只换注入实现、业务代码不动。新增消息类型几乎零成本，拓扑不会漏配。

<p align="center">
  <img alt="消息队列抽象框架：三方法消息契约 + 启动时自动扫描注册" src="./docs/assets/sketches/sketch-mq.png" width="680">
</p>

## 在线体验

线上地址：[http://linkrag.cn/](http://linkrag.cn/)。上传文档、自动构建知识库，围绕内容直接提问，答案逐字流式返回并溯源到原文片段——你在界面上发起的每一次上传、配置与对话，背后的入口、鉴权、任务编排与状态查询都由本仓承担。

## 关联仓库

LinkRag 由三个仓库协作组成：

| 仓库 | 角色 |
| --- | --- |
| [ql-link/LinkRag](https://github.com/ql-link/LinkRag) | Python RAG 服务：文档解析、分片、向量化、索引与召回 |
| [ql-link/LinkRag-Service](https://github.com/ql-link/LinkRag-Service)（本仓） | Java 管理端：用户、配置、文件入口、任务下发与终态回收 |
| [ql-link/LinkRag-Web](https://github.com/ql-link/LinkRag-Web) | 前端：知识库管理与交互界面 |

## 架构导览

LinkRag-Service 是 Maven 多模块项目，依赖自上而下逐层复用：

```text
link-api         # Controller 与 Spring Boot 启动入口
link-service     # 核心业务服务（用户 / 配置 / 数据集 / 文件 / 解析 / 用量 / 召回 / 缓存补偿）
link-mapper      # MyBatis-Plus Mapper
link-components  # Redis / MQ / OSS 横向组件
link-core        # 异常体系、全局异常处理、认证上下文、加密与基础工具
link-model       # Entity、请求 / 响应 DTO、枚举、统一响应模型
```

两端协作的四条共享通道与详细边界，见内部文档：

- [project_structure.md](docs/internals/project_structure.md) — 模块边界与依赖方向
- [cache_module.md](docs/internals/cache_module.md) — Redis 读保护、同步删除与 CDC 补偿
- [mq_module.md](docs/internals/mq_module.md) — MQ 组件抽象与拓扑自注册
- [object_storage_module.md](docs/internals/object_storage_module.md) — OSS / MinIO 对象存储
- [document_file_module.md](docs/internals/document_file_module.md) — 知识文件上传与解析协作

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 2.5.3 |
| 构建 | Maven 多模块 |
| ORM | MyBatis-Plus |
| 鉴权 | Sa-Token |
| 数据库 | MySQL 8（共享库 `tolink_rag_db`） |
| 缓存 | Redis / Lettuce |
| MQ | Kafka / RabbitMQ 组件抽象（默认 Kafka） |
| 文件 | 本地存储 / MinIO OSS 组件 |
| 测试 | JUnit 5、Mockito、SpringBootTest、MockMvc |

## 快速开始

### 1. 初始化数据库

```bash
mysql -h <DB_HOST> -u root -p < scripts/db/schema.sql
mysql -h <DB_HOST> -u root -p tolink_rag_db < scripts/db/init.sql
```

> 数据库 schema 的权威源在 Python 端的 Alembic 迁移，本仓 `scripts/db` 仅供本地与测试使用。

### 2. 配置环境变量

核心变量（完整说明见 [docs/ops/configuration.md](docs/ops/configuration.md)）：

| 变量 | 说明 |
| --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB` | Redis 连接 |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 地址 |
| `TOLINK_MQ_VENDOR` | MQ 实现，默认 `kafka` |
| `OSS_SERVICE_TYPE` / `OSS_FILE_ROOT_PATH` / `OSS_MINIO_*` | OSS 实现与配置 |
| `LLM_SECRET` | API Key 加密密钥，64 位十六进制字符串 |

### 3. 启动与测试

```bash
mvn spring-boot:run -pl link-api    # 启动服务，默认端口 8080

mvn clean test                      # 全量测试
mvn -pl link-service test           # 单模块测试
```

### 4. 容器化部署

```bash
cd deploy
docker compose up -d
```

服务通过环境变量对接外部 MySQL / Redis / Kafka / OSS，详见 [docs/ops/deployment.md](docs/ops/deployment.md)。

## 深入文档

完整导航见 [docs/README.md](docs/README.md)。常用入口：

- **对外契约**：[API](docs/api/api_contracts.md) / [MySQL Schema](docs/api/mysql_schema.md) / [MQ 消息](docs/api/mq_contracts.md) / [错误码](docs/api/error_codes.md)
- **内部实现**：[project_structure](docs/internals/project_structure.md) / [cache_module](docs/internals/cache_module.md) / [mq_module](docs/internals/mq_module.md) / [object_storage_module](docs/internals/object_storage_module.md) / [document_file_module](docs/internals/document_file_module.md)
- **部署与配置**：[configuration](docs/ops/configuration.md) / [deployment](docs/ops/deployment.md) / [integration](docs/ops/integration.md)
- **贡献者规范**：[docs/contributing.md](docs/contributing.md) — 分支、提交、测试、文档同步、Spec-as-Test 流程
- **项目入口（AI / 新成员）**：[CLAUDE.md](CLAUDE.md)

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
