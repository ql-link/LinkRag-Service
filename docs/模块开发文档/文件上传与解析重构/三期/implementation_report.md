# ToLink Service 文件上传与解析重构三期改造报告

> **文档状态：** 已定稿
> **项目名称**：ToLink Service
> **模块名称**：文件上传与解析重构（三期）
> **需求文档**：`docs/模块开发文档/文件上传与解析重构/三期/requirement.md`
> **技术文档**：`docs/模块开发文档/文件上传与解析重构/三期/technical_design.md`
> **分支名称**：`refactor/update-file-upload-parse`
> **负责人：** Fang / Codex
> **最后更新时间：** 2026-04-30

---

## 1. 文档修订记录 (Change Log)

| 版本号 | 修改日期 | 修改内容简述 | 修改人 | 审核人 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-04-30 | 初始化三期改造报告，记录 parse_result MQ 主链路落地结果 | Fang / Codex | 待审核 |

---

## 2. 改造背景与目标 (Overview)

### 2.1 改造背景

三期在二期“Java 发 `parse_task`、Python 写库、HTTP 回调转发 SSE”的基础上，把 Python -> Java 的终态结果通知从内部回调调整为 `parse_result` MQ，并同步收缩内部回调职责。

### 2.2 本次改造目标

- 落地三期 `parse_result` 扁平 JSON 契约。
- 让 Java 默认启用结果 MQ 消费链路。
- 让 Java 消费结果后只做消息校验与 SSE 终态转发，不写主业务表。
- 让内部回调入口只保留 `processing/progress`。

### 2.3 本报告适用范围

**本报告重点说明：**

- 实际改动清单
- 实际落地位置
- 与技术方案差异
- 风险与后续事项

**本报告不重复展开：**

- 完整需求背景
- 完整技术方案推演
- 完整测试执行细节

---

## 3. 实际改造清单 (Implementation Inventory)

### 3.1 模块改动清单

| 模块 | 改动类型 | 实际改动内容 | 备注 |
| :--- | :--- | :--- | :--- |
| `link-api` | 修改 | 收缩内部回调接口为只接受进度事件；补控制层测试 | 三期主链路调整 |
| `link-service` | 修改 | 重定义 `parse_result` 消息模型、启用正式消费者、补结果转发服务逻辑与测试 | 三期核心改造 |
| `link-model` | 修改 | 更新回调 DTO 注释与 Schema 口径 | 保持接口注释与实现一致 |
| `link-mapper` | 无 | 复用已有 Mapper | 无结构改动 |
| `link-core` | 无 | 继续复用 `BusinessException` | 无结构改动 |
| `link-components` | 无 | 继续复用 MQ 抽象与 Kafka 适配 | 不改 framework |

### 3.2 文件与类改动清单

| 文件/类 | 改动类型 | 实际职责变化 | 备注 |
| :--- | :--- | :--- | :--- |
| `InternalKnowledgeFileController` | 修改 | 内部回调拒绝 `success/failed`，只保留 `processing/progress` | 与三期职责边界一致 |
| `KnowledgeParseResultMQ` | 修改 | 从历史 envelope + payload 废案切换为扁平 JSON 终态消息 | 新契约核心 |
| `KnowledgeParseResultKafkaReceiver` | 修改 | 默认消费组改为 `tolink-document-prase` | 与三期约定一致 |
| `KnowledgeParseResultConsumer` | 修改 | 从条件启用改为正式默认启用 | 三期正式主链路 |
| `KnowledgeParseResultServiceImpl` | 修改 | 从旧兼容日志逻辑切换为“校验 + SSE 转发” | 不写主业务表 |
| `KnowledgeParseSseService` / `KnowledgeParseSseServiceImpl` | 修改 | 新增终态结果事件转发能力 | 与现有进度 SSE 复用同一通道 |
| `KnowledgeParseCallbackRequest` | 修改 | 注释和 Schema 改为只描述进度事件 | 文档收口 |
| `KnowledgeParseResultMQTest` | 新增 | 覆盖新消息模型校验 | 三期新增测试 |

### 3.3 接口与配置改动清单

| 类型 | 名称 | 改动说明 | 影响范围 |
| :--- | :--- | :--- | :--- |
| API | `POST /api/v1/internal/parse-tasks/{taskId}/events` | 只接受 `processing/progress` | Python -> Java 进度回调 |
| 消费者 | `KnowledgeParseResultKafkaReceiver` | 默认正式启用 | Python -> Java 终态结果回传 |
| 配置项 | `tolink.mq.parse-result.group-id` | 默认值改为 `tolink-document-prase` | Kafka 消费组 |

### 3.4 数据与中间件改动清单

| 组件 | 名称/Key/Topic/Path | 改动说明 | 是否涉及契约更新 |
| :--- | :--- | :--- | :--- |
| MySQL | 无 | 不改表结构 | 否 |
| Redis | 无 | 不改缓存策略 | 否 |
| MQ | `tolink.rag.parse_result` | 三期正式切换为扁平 JSON 终态消息 | 是 |
| OSS | 无 | 不改对象存储 | 否 |
| 其他 | SSE | 继续沿用现有 `parse` 事件名，新增终态结果转发 | 否 |

---

## 4. 实际落地实现说明 (What Was Built)

### 4.1 核心实现路径

实际代码落地遵循三期技术方案：

`Python 写库终态 -> Python 发送 parse_result -> Java Kafka 消费 -> Java 校验 document_parse_log / 原文件归属 -> Java SSE 推终态 -> 前端必要时查库兜底`

### 4.2 关键实现点

| 实现点 | 实际落地位置 | 处理说明 |
| :--- | :--- | :--- |
| `parse_result` 新契约 | `link-service/.../KnowledgeParseResultMQ` | 使用扁平 JSON，强校验 `document_parse_log_id`、`task_status`、`failure_reason`、`parse_finished_at` |
| 结果消费默认启用 | `KnowledgeParseResultConsumer` | 不再依赖旧 `legacy-parse-result-enabled` 开关 |
| 结果校验与转发 | `KnowledgeParseResultServiceImpl` | 先校验 parse_log、task_id、fileId、datasetId、userId，再转发 SSE |
| SSE 终态事件 | `KnowledgeParseSseServiceImpl.publishResultEvent(...)` | 复用文件维度 SSE 通道，输出 `parse_success / parse_failed` |
| 回调职责收缩 | `InternalKnowledgeFileController.parseEvent(...)` | 拒绝 `success/failed`，要求 `progress` 事件必须带进度值 |

### 4.3 关键注释与设计意图

- 在 `KnowledgeParseCallbackRequest` 中把 DTO 说明收口到“只承接进度事件”，避免后续误把终态继续塞回回调链路。
- 在 `KnowledgeParseResultServiceImpl` 中保留校验日志输出，确保三期结果链路排障时能按 `task_id`、`document_parse_log_id`、`fileId` 定位。

### 4.4 未纳入实现的部分

- 不做 `parse_task` 更强发送确认。
- 不做悬空解析任务失败化治理。
- 不做多实例 SSE 广播增强。

---

## 5. 与技术方案差异说明 (Delta From Technical Design)

### 5.1 差异清单

| 技术方案项 | 原方案 | 实际实现 | 差异原因 | 是否已重新确认 |
| :--- | :--- | :--- | :--- | :--- |
| 三期实现前先产出技术文档 | 先文档后代码 | 实际中曾短暂先改代码，后补技术文档并对齐实现 | 执行顺序一度跑快，已纠正 | 是 |

### 5.2 差异影响分析

- 对上游调用的影响：无新增前端调用方式变化。
- 对下游模块的影响：Python 终态回调需切换为结果 MQ。
- 对测试范围的影响：新增消息模型、结果消费和内部回调校验测试。
- 对发布与回滚的影响：若回滚三期，需要同步恢复终态回调口径。

---

## 6. 风险、遗留问题与后续事项 (Risks & Follow-up)

### 6.1 当前已知风险

- `tolink-document-prase` 消费组名称沿用需求约定的当前拼写，后续若要修正需要评估兼容影响。
- Kafka 结果消费者默认启用后，在无 broker 的测试/本地环境会看到连接告警，但不影响当前定向测试结论。

### 6.2 遗留问题

- `parse_task` 发送失败仍只按同步异常判定。
- `latest_parse_task_id` 已存在但长时间无 `document_parse_log` 的悬空任务尚未治理。

### 6.3 后续建议动作

- 四期评估 Kafka ack、Outbox、本地消息表等可靠投递增强方案。
- 四期设计悬空解析任务失败化巡检或补偿策略。
- 在测试交付阶段补充 Python 联调结果与回滚步骤。

---

## 7. 回写检查 (Update Checklist)

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填实现摘要 | 是 | 三期状态与文档清单已更新 |
| `middleware_contract.md` 已按需更新 | 是 | 已回写三期 `parse_result` 契约 |
| `project_info.md` 已按需更新 | 是 | 已更新当前 MQ 能力现状 |
| 已通知测试阶段关注实现差异 | 是 | 已在本报告与技术文档中记录 |
