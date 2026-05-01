# ToLink Service 文件解析 MQ 投递与解析日志重构（二期）改造报告

> **文档状态：** 评审中
> **项目名称：** ToLink Service
> **模块名称：** 文件解析 MQ 投递与解析日志重构（二期）
> **需求文档：** `docs/模块开发文档/文件上传与解析重构/二期/requirement.md`
> **技术文档：** `docs/模块开发文档/文件上传与解析重构/二期/technical_design.md`
> **分支名称：** `refactor/update-file-upload-parse`
> **负责人：** Codex
> **最后更新时间：** 2026-04-30

---

## 1. 文档修订记录 (Change Log)

| 版本号 | 修改日期 | 修改内容简述 | 修改人 | 审核人 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-04-30 | 按二期已落地代码重写改造报告，纠正旧版与真实实现不一致的事务与交付描述 | Codex | 待审核 |

---

## 2. 改造背景与目标 (Overview)

### 2.1 改造背景

二期目标是把“解析请求提交”与“解析执行写库”拆清：Java 负责受理解析、更新最新任务指针并同步发送 `parse_task` MQ；Python 负责收到 MQ 后创建解析日志、执行解析、写终态并通过内部回调把进度和终态通知回 Java。

### 2.2 本次改造目标

- 重写 Java 侧解析提交主链路，移除旧的“afterCommit 发送 + 补偿重投”思路。
- 保证 `latest_parse_task_id` 更新与 MQ 发送位于同一事务边界，发送失败时整体回滚。
- 打通上传成功后 `parseImmediately=true` 的自动解析提交。
- 统一前端“待解析 / 解析中 / 解析成功 / 解析失败 / 解析提交失败”语义，其中提交成功后立即返回 `parsing`。
- 用测试覆盖二期核心行为，并统一以 `document_parse_log` 作为解析日志表事实。

### 2.3 本报告适用范围

**本报告重点说明：**

- 实际修改了哪些模块、类、测试和文档
- 最终真正落地的事务边界、MQ 消息体和状态语义
- 与技术方案相比的少量实现差异
- 当前仍保留的风险和后续事项

**本报告不重复展开：**

- 完整需求背景
- 完整技术设计推导
- 全量测试执行记录

---

## 3. 实际改造清单 (Implementation Inventory)

### 3.1 模块改动清单

| 模块 | 改动类型 | 实际改动内容 | 备注 |
| :--- | :--- | :--- | :--- |
| `link-service` | 修改 | 重写解析提交服务、自动解析提交流程、MQ 消息校验和结果查询逻辑 | 二期主实现 |
| `link-api` | 修改 | 保持原 API 路径不变，修正注释和提交成功状态口径 | 面向前端与 Python 的入口延续 |
| `link-model` | 修改 | 修正解析提交响应 DTO 的状态说明 | 仅 DTO 语义调整 |
| `docs/模块开发文档/.../二期` | 修改 | 回填 `feature_info.md`，重写 `implementation_report.md`，修正文档状态口径 | 交付留痕 |

### 3.2 文件与类改动清单

| 文件/类 | 改动类型 | 实际职责变化 | 备注 |
| :--- | :--- | :--- | :--- |
| `link-service/src/main/java/com/qingluo/link/service/impl/KnowledgeParseTaskServiceImpl.java` | 修改 | 重写手动解析、自动解析、当前任务查询和前端状态映射 | 二期核心逻辑 |
| `link-service/src/main/java/com/qingluo/link/service/impl/KnowledgeFileServiceImpl.java` | 修改 | 上传成功后在事务提交后触发自动解析提交 | 保持上传与解析事务解耦 |
| `link-service/src/main/java/com/qingluo/link/service/mq/KnowledgeParseTaskMQ.java` | 修改 | 强制校验 `parsed_file_id` 等二期必填字段 | 落实跨模块契约 |
| `link-api/src/main/java/com/qingluo/link/api/controller/KnowledgeFileController.java` | 修改 | 更新接口注释，明确解析提交成功后返回 `parsing` | API 路径未改 |
| `link-model/src/main/java/com/qingluo/link/model/dto/response/FileParseSubmitDTO.java` | 修改 | 修正提交响应的状态说明与示例值 | 与 PRD/技术文档对齐 |
| `link-service/src/test/java/com/qingluo/link/service/impl/KnowledgeParseTaskServiceImplPhase2Test.java` | 新增/修改 | 覆盖手动解析、自动解析、拒绝重复提交、查询状态映射 | 二期核心回归保护 |
| `link-service/src/test/java/com/qingluo/link/service/mq/KnowledgeParseTaskMQTest.java` | 修改 | 覆盖 `parsed_file_id` 必填校验 | 契约测试 |
| `link-api/src/test/java/com/qingluo/link/api/controller/KnowledgeFileControllerTest.java` | 修改 | 覆盖上传即解析、手动解析响应和结果查询接口口径 | API 行为测试 |

### 3.3 接口与配置改动清单

| 类型 | 名称 | 改动说明 | 影响范围 |
| :--- | :--- | :--- | :--- |
| API | `POST /api/v1/files/{fileId}/parse` | 返回结构不变，但提交成功时 `frontendStatus` 收敛为 `parsing` | 前端解析提交 |
| API | `POST /api/v1/internal/parse-tasks/{taskId}/events` | 本次未改路径，仅沿用既有回调入口承接 Python 事件 | Python 内部回调 |
| 配置项 | 无新增 | 未引入新配置，仅复用现有 `internal-base-url`、`service-token`、SSE timeout | 无新增运维负担 |
| 定时任务 | `compensateCreatedTasksOnSchedule()` | 保留空实现，避免历史调度配置报错 | 不再承担补偿主链路职责 |

### 3.4 数据与中间件改动清单

| 组件 | 名称/Key/Topic/Path | 改动说明 | 是否涉及契约更新 |
| :--- | :--- | :--- | :--- |
| MySQL | `document_parsed_file.latest_parse_task_id` | Java 在解析受理事务中更新最新任务指针，MQ 失败时随事务回滚 | 否 |
| MySQL | `document_parse_log` | 解析日志表，按新口径承接每次解析尝试 | 是 |
| MQ | `tolink.rag.parse_task` | 消息体严格要求 `parsed_file_id`、原文件定位和 Markdown 产物定位 | 是 |
| OSS | `rag-raw` / `rag-md` | Java 生成原文件下载定位和 Markdown 目标路径，Python 按消息执行 | 否 |

---

## 4. 实际落地实现说明 (What Was Built)

### 4.1 核心实现路径

```text
前端手动解析 / 上传后自动解析
-> Java 校验原文件、解析聚合记录、进行中任务
-> Java 更新 document_parsed_file.latest_parse_task_id
-> Java 在同一事务内同步发送 parse_task MQ
-> 发送成功：前端进入 parsing
-> 发送失败：事务回滚，前端收到解析提交失败

Python 收到 MQ
-> Python 创建/推进 document_parse_log 并写终态
-> Python 通过内部回调上报 processing/progress/success/failed
-> Java SSE 向前端推送进度与终态
-> 前端断线/刷新/超时未收终态时再查数据库兜底
```

### 4.2 关键实现点

| 实现点 | 实际落地位置 | 处理说明 |
| :--- | :--- | :--- |
| 同事务更新指针并发 MQ | `KnowledgeParseTaskServiceImpl.submitManualParse()` / `submitAutoParseAfterUpload()` | 生成 `task_id`、更新 `latest_parse_task_id`、同步发送 MQ 放在同一个事务方法中，发送异常直接抛 `BusinessException` 触发回滚 |
| 自动解析在上传提交后触发 | `KnowledgeFileServiceImpl.submitAutoParseAfterUploadCommitted()` | 使用事务同步在上传事务 `afterCommit` 后再调用解析提交服务，避免上传成功被解析失败回滚 |
| 当前任务优先按最新指针取 | `KnowledgeParseTaskServiceImpl.resolveCurrentTaskMap()` | 结果查询优先遵循 `document_parsed_file.latest_parse_task_id`，找不到对应任务时再回退到最近任务 |
| 提交成功状态收敛 | `KnowledgeParseTaskServiceImpl.buildSubmitDTO()` | 解析提交成功后直接返回 `parsing`，不再返回 `parse_waiting` |
| MQ 消息契约硬校验 | `KnowledgeParseTaskMQ.validate(...)` | `task_id`、`original_file_id`、`parsed_file_id`、原文件定位和 Markdown 目标定位缺一不可 |
| 旧补偿任务兼容保留 | `KnowledgeParseTaskServiceImpl.compensateCreatedTasksOnSchedule()` | 保留空壳方法，避免历史调度配置报错，但不再做补偿重投 |
| Java 不预写解析日志 | `KnowledgeParseTaskServiceImpl.hasRunningTask(...)` | 通过 `latest_parse_task_id` + `document_parse_log` 查询识别“已投递但 Python 未建日志”的窗口，防止重复提交 |

### 4.3 关键注释与设计意图

- `KnowledgeParseTaskServiceImpl` 中补了事务边界注释，明确“最新 task 指针必须与 MQ 发送在同一事务里”，避免再次回到会产生假“解析中”的实现。
- `KnowledgeFileServiceImpl` 中补了上传与解析解耦注释，明确上传成功是独立事实，自动解析失败不能回滚上传。
- `KnowledgeFileController` 和 `FileParseSubmitDTO` 中补了接口语义说明，避免前端继续按旧的 `parse_waiting` 口径理解解析提交结果。

### 4.4 未纳入实现的部分

- 不引入 Outbox / 本地消息表。
- 不改 Python 仓库实现，仅约束其应遵守的 MQ 和回调契约。
- 不解决多实例 SSE 广播一致性。

---

## 5. 与技术方案差异说明 (Delta From Technical Design)

### 5.1 差异清单

| 技术方案项 | 原方案 | 实际实现 | 差异原因 | 是否已重新确认 |
| :--- | :--- | :--- | :--- | :--- |
| 自动解析落地点 | 技术文档只定义“上传成功后触发解析提交” | 实际放在上传事务 `afterCommit` 中调用独立解析提交服务 | 这样可保证上传成功与解析提交失败的事务边界清晰 | 是 |

### 5.2 差异影响分析

- 对上游调用的影响：
  前端接口路径不变，但解析提交成功的状态口径需要按 `parsing` 接收。
- 对下游模块的影响：
  Python 需要严格按新增后的 `parsed_file_id` 契约消费 MQ。
- 对测试范围的影响：
  需要同时验证“手动解析同步回滚语义”和“上传成功后自动解析异步触发语义”。
- 对发布与回滚的影响：
  本期不涉及真实 DDL 迁移，主要风险集中在 Java 业务逻辑与 Python 契约配合。

---

## 6. 风险、遗留问题与后续事项 (Risks & Follow-up)

### 6.1 当前已知风险

- 当前 SSE 仍是单机内存实现，多实例部署下只保证数据库终态一致，不保证任意实例都能实时收到同一条进度事件。

### 6.2 遗留问题

- 旧 `parse_result` 兼容链路代码仍在仓库中，但二期主路径已经不依赖它。
- `project_info.md` 尚未在本次实现阶段回写，按流程应在测试交付阶段统一更新。

### 6.3 后续建议动作

1. 由 Python 侧对齐 `parsed_file_id`、`md_bucket`、`md_object_key` 契约并联调。
2. 在测试交付阶段补齐 `testing_delivery.md`，重点覆盖 MQ 失败回滚、自动解析、SSE 兜底查库。
3. 三期如要增强一致性，再评估 Outbox / 本地消息表。
4. 后续继续梳理旧 `parse_result` 兼容链路与历史空壳调度，降低误用风险。

---

## 7. 回写检查 (Update Checklist)

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填实现摘要 | 是 | 当前状态已更新为“代码实现完成，待测试交付” |
| `middleware_contract.md` 已按需更新 | 否 | 本次未单独改公共契约文档 |
| `project_info.md` 已按需更新 | 否 | 建议在测试交付阶段一并回写 |
| 已通知测试阶段关注实现差异 | 是 | 本报告已记录事务边界、兼容表名和 MQ 契约重点 |
