# 文件上传与解析协同重构二期测试执行与交付记录

> **文档状态：** 已交付
> **项目名称**：ToLink Service
> **模块名称**：文件上传与解析协同重构
> **当前期次**：二期
> **需求文档**：`docs/模块开发文档/文件上传与解析/二期/requirement.md`
> **技术文档**：`docs/模块开发文档/文件上传与解析/二期/technical_design.md`
> **改造报告**：`docs/模块开发文档/文件上传与解析/二期/implementation_report.md`
> **分支名称**：`feature/knowledge_file_upload_and_parse`
> **执行人：** Codex
> **最后更新时间：** 2026-04-26

---

## 1. 使用说明

本文件用于指导开发者或测试执行者完成二期解析协同链路验证，并记录当前已经执行的自动化测试结果。

使用要求：

- 先确认数据库结构、MQ 配置和 OSS 配置已与当前环境一致。
- 再执行自动化测试命令。
- 与 Python 端联调时，需要单独验证 MQ 消息消费、Python 写库和回调接口。
- 有失败项时必须记录问题现象、影响范围和处理结论。

---

## 2. 测试范围与目标

### 2.1 本次要验证的内容

- 上传成功且开启自动解析时，Java 创建解析任务。
- 手动点击解析时，Java 创建解析任务并返回 `task_id`。
- 同一原文件存在 `created` 或 `processing` 任务时，不允许重复点击解析。
- Java 投递给 Python 的解析任务 MQ 使用扁平 snake_case JSON，包含 `user_id`、`dataset_id` 和 `rag-md` 产物 bucket。
- 前端可按文件订阅 SSE 解析事件，Python 可通过内部接口回调 Java 转发进度。
- 前端可按本次文件列表查询全部文件解析结果。
- 旧解析结果 MQ 消费链路默认关闭，避免 Java 端继续写解析结果。
- 已成功投递过的 `created` 解析任务不会被补偿任务重复投递。

### 2.2 本次不验证的内容

- Python 端真实解析逻辑。
- Python 端真实写入 `document_parse_task` 与 `document_parsed_file` 的联调结果。
- 真实 MQ broker 的网络投递、消费确认和积压治理。
- SSE 多实例部署下的连接路由问题。
- 原文件软删除与解析产物删除链路。

### 2.3 验收项映射

| 验收项 | 对应用例编号 | 是否覆盖 | 备注 |
| :--- | :--- | :--- | :--- |
| 自动解析创建任务 | TC-01 | 是 | API 集成测试覆盖 |
| 手动解析创建任务 | TC-02 | 是 | API 集成测试覆盖 |
| 重复点击解析限制 | TC-E01 | 是 | API 集成测试覆盖 |
| MQ 消息体契约 | TC-03 | 是 | 服务层单测覆盖 |
| 解析结果列表查询 | TC-04 | 是 | API 集成测试覆盖 |
| 已投递任务不重复补偿 | TC-E05 | 是 | 服务层单测覆盖 |
| 旧 MQ 默认关闭 | TC-R02 | 是 | Spring 装配测试覆盖 |
| Python 真实消费与写库 | TC-I01 | 否 | 需要 Python 端联调 |

---

## 3. 测试前提与环境准备

### 3.1 环境信息

| 项目 | 内容 |
| :--- | :--- |
| 分支 | `feature/knowledge_file_upload_and_parse` |
| 部署环境 | 本地自动化测试 |
| 服务状态 | Maven 多模块测试执行通过 |
| 外部依赖 | 自动化测试使用 H2、MockMvc、本地 mock OSS；未连接真实 MQ 与真实 Python |
| 相关配置 | `qingluopay.mq.vender=none`、`tolink.knowledge-file.legacy-parse-result-enabled` 默认关闭 |

### 3.2 测试前提

- Java 版本为 17。
- Maven 可正常构建多模块项目。
- `link-api/src/test/resources/schema.sql` 已包含二期表结构。
- 解析任务 MQ 在测试中只校验消息体，不依赖真实 broker。

### 3.3 测试数据准备

| 数据项 | 用途 | 准备方式 | 备注 |
| :--- | :--- | :--- | :--- |
| 测试用户 | 校验用户资源归属 | `KnowledgeFileControllerTest` 初始化 | 用户 ID 为测试固定值 |
| 测试数据集 | 校验数据集归属 | `KnowledgeFileControllerTest` 初始化 | 数据集 ID 为测试固定值 |
| 原文件记录 | 自动解析、手动解析、结果查询 | 测试内插入或通过上传接口创建 | H2 内存库 |
| 解析任务记录 | 防重复点击与结果查询 | 测试内插入 | 覆盖 `created`、`success`、`failed` |

### 3.4 执行方式

- 单元测试：已执行。
- 集成测试：已执行。
- 接口调试：通过 MockMvc 覆盖。
- 日志检查：已检查测试输出，无阻塞错误。
- 数据库检查：通过 H2 SQL 与 Mapper 断言覆盖。
- MQ 检查：通过消息体序列化测试覆盖。
- OSS 检查：沿用一期本地 mock 上传链路。

---

## 4. 测试执行清单

### 4.1 主流程测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-01 | 上传后自动解析 | 已存在用户和数据集，上传参数 `parseImmediately=true` | 执行 `KnowledgeFileControllerTest` 上传接口测试 | 上传成功后创建一条解析任务 | 已创建解析任务，测试通过 | 通过 | Codex |
| TC-02 | 手动提交解析 | 原文件上传状态为 `success` | 调用 `POST /api/v1/files/{fileId}/parse` | 返回 `task_id`，任务状态为 `created` | 返回解析任务提交结果，测试通过 | 通过 | Codex |
| TC-03 | MQ 消息体契约 | 构造解析任务 payload | 执行 `KnowledgeParseTaskMQTest` | JSON 无 envelope，字段为 snake_case，bucket 为 `rag-md` | 断言通过 | 通过 | Codex |
| TC-04 | 查询本次文件解析结果 | 准备多条原文件和解析任务记录 | 调用 `GET /api/v1/datasets/{datasetId}/files/parse-results` | 返回请求文件列表中的全部文件解析状态 | 成功返回全部文件状态，测试通过 | 通过 | Codex |

### 4.2 异常与边界测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-E01 | 重复点击解析 | 同一原文件已有 `created` 任务 | 再次调用手动解析接口 | 拒绝创建新任务 | 已拒绝重复解析，测试通过 | 通过 | Codex |
| TC-E02 | MQ 任务消息缺少 `task_id` | 构造空 `task_id` payload | 调用 `KnowledgeParseTaskMQ.getMessage()` | 抛出参数异常 | 已抛出 `parse_task task_id is missing`，测试通过 | 通过 | Codex |
| TC-E03 | 旧解析结果消费者默认关闭 | Spring Boot 测试默认配置 | 检查 `KnowledgeParseResultKafkaReceiver` Bean | 默认不装配旧消费者 | Bean 不存在，测试通过 | 通过 | Codex |
| TC-E04 | Python 进度回调参数越界 | 需要接口级联调 | 调用内部回调接口传入越界 progress | Java 拒绝非法进度 | 尚未执行真实接口联调 | 未执行 | Codex |
| TC-E05 | 已成功投递的 `created` 任务不重复补偿 | 解析任务已有 `last_dispatched_at` 且无 `last_dispatch_error` | 执行 `compensateCreatedTasks()` | 不再次调用 MQ 发送 | 已通过服务层单测 | 通过 | Codex |

### 4.3 回归检查项

| 检查项 | 检查方式 | 预期结果 | 实际结果 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| 一期上传接口兼容性 | `KnowledgeFileControllerTest` | 原文件仍可上传并写库 | 通过 | 通过 |
| 一期上传失败/重试逻辑 | `KnowledgeFileControllerTest` | 上传失败与重试逻辑不回归 | 通过 | 通过 |
| 删除链路未扩大范围 | `KnowledgeFileServiceImplTest` | 原删除补偿逻辑保持原行为 | 通过 | 通过 |
| 旧解析结果服务单测 | `KnowledgeParseResultServiceImplTest` | 旧服务代码仍可编译并通过既有单测 | 通过 | 通过 |

---

## 5. 执行证据记录

### 5.1 接口与页面结果

- `KnowledgeFileControllerTest` 已通过 MockMvc 验证上传、自动解析任务创建、手动解析、防重复点击、解析结果查询。
- 未进行真实前端页面操作验证，前端联调需在 UI 完成后补充。

### 5.2 日志与链路记录

- `mvn -pl link-api -am -Dtest=KnowledgeFileControllerTest,KnowledgeParseResultIntegrationTest test` 执行通过，结果为 `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -pl link-service -am -Dtest=KnowledgeParseTaskMQTest,KnowledgeParseTaskServiceImplTest,KnowledgeFileServiceImplTest,KnowledgeParseResultServiceImplTest,KnowledgeParseResultKafkaReceiverTest test` 执行通过，结果为 `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`。

### 5.3 数据库 / 缓存 / MQ / OSS 校验结果

- 数据库：H2 schema 已包含 `document_parse_task` 与重定义后的 `document_parsed_file`，API 集成测试通过 Mapper 断言完成验证。
- MQ：解析任务消息体已通过单测验证为扁平 snake_case JSON，`md_bucket` 为 `rag-md`。
- OSS：本次未连接真实 MinIO；上传路径沿用一期本地 mock 验证。
- Redis：二期未引入 Redis。

---

## 6. 问题记录与处理结果

| 编号 | 问题现象 | 影响范围 | 严重级别 | 当前状态 | 处理结果 / 临时结论 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-01 | 解析任务 MQ 消息体默认输出 camelCase，测试读取不到 `task_id` | Python 消息消费契约 | 高 | 已修复 | 在 `KnowledgeParseTaskMQ.MsgPayload` 字段上增加 `@JSONField`，固定 snake_case 输出 |
| BUG-02 | MQ 已投递成功但任务仍为 `created` 时，补偿任务可能 30 秒后重复投递同一 `task_id` | Python 重复消费风险 | 高 | 已修复 | 补偿逻辑改为只重投未投递过或上次投递失败的任务，并补充服务层单测 |

---

## 7. 交付结论

### 7.1 测试结论

- 主流程是否通过：通过，自动解析任务创建、手动解析、结果查询已由自动化测试覆盖。
- 异常流程是否通过：通过，重复点击解析限制、MQ 必填字段校验、旧消费者默认关闭已覆盖。
- 回归检查是否通过：通过，目标回归测试均已通过。

### 7.2 遗留风险

- 未连接真实 MQ broker 与 Python 服务，真实消费、Python 写库、回调 Java 和前端 SSE 展示仍需联调验证。
- SSE 当前是单机内存连接，多实例部署问题已明确放入三期。
- MQ 投递失败采用内部补偿重试，未引入 outbox 或事务消息，极端宕机场景放三期治理。

### 7.3 是否允许交付

- 是否可交付：有条件可交付。
- 交付前提：Java 二期代码可进入联调环境；Python 端按二期 MQ、表结构、回调接口契约完成对接。
- 联调注意事项：重点验证 `task_id` 与 `document_parse_task.task_id` 一致、`md_bucket=rag-md`、Python 只更新解析任务和解析产物，不通过 Java 端写解析结果。
- 发布 / 回滚注意事项：旧解析结果消费者默认关闭，如需回滚旧链路需显式配置 `tolink.knowledge-file.legacy-parse-result-enabled=true` 并确认表结构兼容。

---

## 8. 回写检查

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填测试结论 | 是 | 已更新为待最终审核 |
| `project_info.md` 已同步更新 | 是 | 已更新 storage、mq 与最近变更 |
| 本次遗留风险已显式记录 | 是 | 见 7.2 |
| 交付结论已明确 | 是 | 有条件可交付，需 Python 联调 |
