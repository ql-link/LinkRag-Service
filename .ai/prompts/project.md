# toLink-Service

`toLink-Service` 是 ToLink 的 Java 管理端服务，基于 Spring Boot 和 Maven 多模块组织，负责用户、LLM 配置、数据集、知识文件、OSS 文件入口、MQ 解析任务投递与解析结果回传。

本文件是项目的 **AI Agent 与贡献者统一入口**。新需求开发采用 Spec-as-Test 工作流：`brief.md → acceptance.feature → technical_design.md → Code + Tests`。旧七阶段 `requirement.md / testing_delivery.md` 流程已废弃。

---

## 一、项目使用

### 1.1 主要代码入口

| 入口 | 路径 |
| --- | --- |
| Spring Boot 启动类 | `link-api/src/main/java/com/qingluo/link/api/LinkApplication.java` |
| HTTP Controller | `link-api/src/main/java/com/qingluo/link/api/controller` |
| 业务 Service | `link-service/src/main/java/com/qingluo/link/service` |
| Entity / Request / Response / Enum | `link-model/src/main/java/com/qingluo/link/model` |
| Mapper | `link-mapper/src/main/java/com/qingluo/link/mapper` |
| Redis / MQ / OSS 组件 | `link-components/` |
| 全局异常、认证上下文、工具类 | `link-core/src/main/java/com/qingluo/link/core` |
| 数据库脚本 | `scripts/db/schema.sql`、`scripts/db/init.sql` |

### 1.2 常用命令

```bash
# 全量测试
mvn test

# 单模块测试
mvn -pl link-service test
mvn -pl link-api test

# 启动服务
mvn spring-boot:run -pl link-api

# AI 资产链接与文档同步校验
python3 scripts/setup_ai_links.py
python3 scripts/check_ai_links.py
python3 scripts/check_docs_sync.py --working
```

服务默认端口：`8080`。Swagger/Knife4j 入口以当前 Springfox/Knife4j 配置为准。

### 1.3 模块职责

| 模块 | 职责 |
| --- | --- |
| `link-model` | Entity、请求/响应 DTO、枚举、统一响应模型 |
| `link-core` | 异常体系、全局异常处理、认证上下文、加密与基础工具 |
| `link-components` | Redis、MQ、OSS 等横向组件 |
| `link-mapper` | MyBatis-Plus Mapper |
| `link-service` | 用户、配置、数据集、知识文件、解析任务、用量等业务逻辑 |
| `link-api` | Controller、启动入口、接口层配置与测试入口 |

### 1.4 系统边界

ToLink 采用 “Java 管理端 + Python RAG 执行端” 协作模式：

- Java 端负责管理入口、用户态资源、配置、文件上传、对象存储定位、解析任务投递、结果查询与 SSE 转发。
- Python 端负责文档解析、RAG 执行、LLM 调用、解析产物生成与部分状态推进。
- 两端通过 MySQL、MQ、OSS/MinIO 和必要的内部 HTTP 接口协作。

### 1.5 当前项目结构

> 骨架 + 关键入口，由 `agents-tree-sync` skill 维护。`docs/` 内部结构变化不在此树同步。

```
toLink-Service/
├── link-api/                Controller、Spring Boot 启动入口、接口层配置与测试
├── link-service/            业务逻辑（用户 / LLM 配置 / 数据集 / 知识文件 / 解析任务 / 用量）
├── link-model/              Entity、请求/响应 DTO、枚举、统一响应模型
├── link-mapper/             MyBatis-Plus Mapper
├── link-core/               异常体系、全局异常处理、认证上下文、加密与工具
├── link-components/         横向组件
│   ├── toLink-components-mq/     MQ 抽象与多厂商适配
│   ├── toLink-components-oss/    OSS / MinIO 对象存储
│   └── toLink-components-redis/  Redis 缓存
├── docs/                    架构 / 契约 / 指南 / 流程文档
├── .ai/                     AI 入口（prompts/project.md）与 skills/
├── .specs/                  需求产物（brief/acceptance/TD/report，本地不入库）
└── scripts/                 AI 资产链接与文档同步校验
```

---

## 二、Spec-as-Test 工作流

### 2.1 标准产物

每个新需求默认在 `.specs/<需求名>/` 下维护三类产物：

| 产物 | 职责 | 进入条件 |
| --- | --- | --- |
| `brief.md` | 为什么做、做什么、不做什么、业务流程、模块实现思路、风险 | 原始需求进入后生成并迭代 |
| `acceptance.feature` | 用 Gherkin 描述“什么是做对了”，每个 `Then` 必须可断言 | brief 已冻结 |
| `technical_design.md` | 基于 brief、acceptance 和真实代码说明在哪里改、怎么改、如何测 | acceptance 已冻结 |

实现阶段以 `acceptance.feature` 为验收契约。当前项目不引入 Cucumber/JBehave；Scenario 由 JUnit、Mockito、MockMvc、SpringBootTest 等测试承接。

### 2.2 阶段门禁

1. brief 未冻结，不生成 acceptance。
2. acceptance 未冻结，不生成 technical_design。
3. technical_design 未审核，不进入实现。
4. 实现完成后，必须运行匹配范围的 Maven 测试和文档同步校验。
5. 准备提交或合并前，执行代码审查与质量门禁。

### 2.3 Skill 路由

| 用户意图 | 使用 skill |
| --- | --- |
| 新需求、先理清业务、写 brief | `brief-generator` |
| 基于冻结 brief 写验收场景 / Gherkin | `acceptance-generator` |
| 基于 brief + acceptance 写技术方案 | `technical-design` |
| 按已审核 TD 实现代码 | `implementation-execution` |
| 写测试、补测试、修测试 | `auto-test` 或 `tdd` |
| 跑全量测试 | `run-all-tests` |
| 检查中间件 / 跨模块契约 | `contract-guard` |
| 提交前 review / 质量门禁 | `code-review-and-quality` |
| 从 dev 创建分支并发 PR | `branch-pr-workflow` |
| 提 bug issue 或新需求 issue | `cowork-issue-sync` |

---

## 三、文档目录

| 目录 | 职责 |
| --- | --- |
| `docs/internals/` | 项目结构、模块边界、主要业务链路、组件架构、测试约定 |
| `docs/api/` | API、MySQL schema、MQ 消息、错误码等契约 |
| `docs/ops/` | 启动、配置、部署、联调指南 |

开发流程（Spec-as-Test、分支 PR、issue 同步）不再单列文档目录，统一由本文档「二」与对应 skill 承接，贡献者入口见 `docs/contributing.md`。

### 3.1 按任务查阅

| 任务 | 先读 |
| --- | --- |
| 理解项目结构 | `docs/internals/project_structure.md` |
| 修改接口 | `docs/api/api_contracts.md` + 对应 Controller |
| 修改表、Entity、Mapper | `docs/api/mysql_schema.md` + `scripts/db/schema.sql` + Entity |
| 修改 MQ | `docs/api/mq_contracts.md` + `docs/internals/mq_module.md` |
| 修改 Redis 缓存 | `docs/internals/cache_module.md` |
| 修改 OSS / 文件上传 | `docs/internals/object_storage_module.md` + `docs/internals/document_file_module.md` |
| 修改配置 | `docs/ops/configuration.md` |
| 写 / 补测试 | `docs/internals/testing.md` + `auto-test` skill |
| 新需求开发 | 本文档「二、Spec-as-Test 工作流」+ `brief-generator` skill |
| 提 issue / 同步 Linear 与 GitHub | `cowork-issue-sync` skill |

---

## 四、文档同步规则

机器规则位于 `.claude/doc-sync-rules.yaml`，由 `scripts/check_docs_sync.py`、pre-commit 和 GitHub Actions 执行。

高风险变更必须同步文档：

- Controller / API DTO 变更 → `docs/api/api_contracts.md`
- Entity / 数据库脚本变更 → `docs/api/mysql_schema.md`
- MQ 消息或消费者/生产者变更 → `docs/api/mq_contracts.md`、`docs/internals/mq_module.md`
- Redis / OSS / 配置变更 → 对应 internals 或 ops 文档
- `.ai`、入口文档、同步脚本变更 → `docs/contributing.md`

提交前建议执行：

```bash
python3 scripts/check_ai_links.py
python3 scripts/check_docs_sync.py --working
mvn test
```
