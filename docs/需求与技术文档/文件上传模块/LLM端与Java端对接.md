# toLink-Rag MQ 消息中台对接指南

本文档旨在说明 Java 管理端与 toLink-Rag Python 服务之间通过 MQ 进行异步任务通信的约定。主要涵盖 Java 生产端、Python/RAG 消费端、Topic/Queue 及 Payload 结构定义。

---

## 1. 架构概述

Java 与 Python/RAG 之间直接通过 MQ Broker 通信。Java 端在原文件成功存储并创建解析任务记录后，向约定的 Topic/Queue 投递标准 MQ 消息信封；Python/RAG 端订阅对应 Topic/Queue，消费消息后通过 Java 内部下载接口获取原文件并执行解析。

Java 和 Python/RAG 必须连接同一个 MQ Broker，并对 Topic/Queue 名称、Envelope 格式、字段含义和幂等键保持一致。

**消息信封(Envelope)格式**（仅在中间件传输时使用，对接方了解即可）：
```json
{
  "mq_type": "消息类型",
  "mq_name": "目标 Topic / Queue 名称",
  "payload": {
    "message_id": "唯一 UUID",
    "timestamp": 1713330000.123,
    // ... 具体业务字段
  }
}
```

---

## 2. 消息对接明细

目前系统预设了以下主要业务消息场景。首期知识文件解析需要文档解析任务消息和解析结果通知消息。

### 2.1 文档解析任务 (Parse Task)

用于触发 Python 端对通过管理平台上传至 OSS 的文档进行异步解析处理。

*   **MQ 名称**: `tolink.rag.parse_task`
*   **Java 生产端**: Java 端直接投递解析任务 MQ 消息
*   **Python/RAG 消费端**: Python/RAG 订阅并消费解析任务消息

Kafka 建议：

| 配置 | 建议值 | 说明 |
| --- | --- | --- |
| Topic | `tolink.rag.parse_task` | 文档解析任务 Topic |
| Partitions | `3` | 首期支持最多 3 个同 consumer group 的 Python/RAG consumer 并行消费 |
| Replicas | 本地/测试 `1`，生产 `3` | 副本数不能超过 broker 数 |
| Consumer Group | `tolink-rag-parse-worker` | Python/RAG 解析消费者组 |

说明：

- Kafka 分区数只支持增加，不支持减少，不作为临时扩容手段。
- 如出现持续 consumer lag，可评估从 3 扩容到 6 或 12，并同步扩容 Python/RAG consumer。
- 扩分区只影响新消息，旧消息不会自动迁移到新增分区。

RabbitMQ 建议：

| 配置 | 建议值 |
| --- | --- |
| Exchange | `tolink.rag` |
| Routing Key | `parse_task` |
| Queue | `tolink.rag.parse_task` |

**消息体 (Envelope JSON)**
```json
{
  "mq_type": "parse_task",
  "mq_name": "tolink.rag.parse_task",
  "payload": {
    "task_id": "str, 文档解析任务的唯一标识",
    "document_id": "str, Java 原文件记录 ID",
    "file_url": "str, Java 内部原文件下载地址",
    "file_type": "str, 文件格式(md/markdown/pdf/docx/txt 等)"
  }
}
```

**生产端逻辑说明**:

Java 端在两种场景下投递该消息：

- 用户上传文件时选择立即解析：原文件成功写入 `rag-raw` 后，创建解析任务并投递 MQ。
- 用户先上传多个文件后点击解析：对已上传成功的文件创建解析任务并投递 MQ。

上传失败时不投递 MQ。MQ 投递失败时，Java 保留原文件记录，并在 `document_original_file` 中标记通知失败状态，便于后续重试。

**消费端逻辑说明**: 
Python 侧订阅 `tolink.rag.parse_task` 后，通过 `payload.file_url` 下载原文件，进行多模态解析/文本提取。解析完成后，Python/RAG 不直接写 Java 业务表，而是向 `tolink.rag.parse_result` 投递解析结果通知；Java 消费结果消息后只回写 `document_original_file`。Python/RAG 应以 `payload.task_id` 作为幂等键，避免 MQ 重投或 Java 重试导致重复解析。

---

### 2.2 文档解析结果通知 (Parse Result)

用于 Python/RAG 端在解析完成后通知 Java 端回写解析状态和解析结果文件地址。解析结果文件由 Python/RAG 端存储到 MinIO，Java 只保存 MinIO bucket、object key 和访问 URL。

*   **MQ 名称**: `tolink.rag.parse_result`
*   **Python/RAG 生产端**: Python/RAG 解析完成后投递解析结果 MQ 消息
*   **Java 消费端**: Java 订阅并消费解析结果消息，回写 `document_original_file`

Kafka 建议：

| 配置 | 建议值 | 说明 |
| --- | --- | --- |
| Topic | `tolink.rag.parse_result` | 文档解析结果通知 Topic |
| Partitions | `3` | 与解析任务 Topic 保持一致，避免结果通知成为瓶颈 |
| Replicas | 本地/测试 `1`，生产 `3` | 副本数不能超过 broker 数 |
| Consumer Group | `tolink-java-parse-result-worker` | Java 解析结果消费者组 |

RabbitMQ 建议：

| 配置 | 建议值 |
| --- | --- |
| Exchange | `tolink.rag` |
| Routing Key | `parse_result` |
| Queue | `tolink.rag.parse_result` |

**消息体 (Envelope JSON)**
```json
{
  "mq_type": "parse_result",
  "mq_name": "tolink.rag.parse_result",
  "payload": {
    "task_id": "str, 文档解析任务唯一标识",
    "document_id": "str, Java 原文件记录 ID",
    "success": true,
    "status": "success",
    "parsed_bucket_name": "rag-parsed",
    "parsed_object_key": "parsed/2026/04/20/10000/10000.md",
    "parsed_file_url": "str, 解析结果文件访问或内部下载地址",
    "failure_reason": "str, 失败时的安全摘要，成功时为空",
    "time_cost_ms": 1200
  }
}
```

字段规则：

- `task_id` 必须与 Java 投递解析任务时的 `payload.task_id` 一致。
- `document_id` 必须与 Java 原文件记录 ID 一致。
- `success=true` 时，`status=success`，且 `parsed_bucket_name`、`parsed_object_key`、`parsed_file_url` 必填。
- `parsed_bucket_name` 首期固定为 MinIO bucket `rag-parsed`。
- `success=false` 时，`status=failed`，`failure_reason` 必填。
- Java 以 `task_id` 作为幂等键；已成功的任务收到失败消息时忽略，避免乱序覆盖成功状态。

**Java 消费端回写规则**：

Java 消费 `tolink.rag.parse_result` 后，必须先通过 `task_id` 或 `(document_id + task_id)` 找到唯一的 `document_original_file` 记录，再执行状态回写。回写规则统一如下：

- 成功消息：
  - `parse_status = success`
  - `is_parse_success = true`
  - `parsed_bucket_name = payload.parsed_bucket_name`
  - `parsed_object_key = payload.parsed_object_key`
  - `parsed_file_url = payload.parsed_file_url`
  - `parsed_at = 当前回写时间`
  - `parse_failure_reason = null`
  - `failure_reason = null`
- 失败消息：
  - `parse_status = failed`
  - `is_parse_success = false`
  - `parse_failure_reason = payload.failure_reason`
  - `failure_reason` 保持为空或仅保留上传/投递失败语义，不与解析失败原因混用
  - 不覆盖已存在的成功解析结果地址字段

**幂等与乱序处理规则**：

- 若数据库中该文件 `parse_status` 已为 `success`，后续重复成功消息直接忽略。
- 若数据库中该文件 `parse_status` 已为 `success`，后续失败消息直接忽略。
- 若先收到失败消息、后收到成功消息，则允许成功消息覆盖失败状态。
- 若消息中的 `document_id` 存在但 `task_id` 不匹配，Java 必须拒绝回写并记录错误日志。
- 若消息中的文件记录不存在，Java 只记录告警，不做补建。

**日志建议**：

- 成功回写时记录：`task_id`、`document_id`、`dataset_id`、`parsed_object_key`
- 失败回写时记录：`task_id`、`document_id`、`dataset_id`、`failure_reason`
- 幂等忽略时记录：`task_id`、`document_id`、`current_parse_status`

---

### 2.3 缓存同步通知 (Cache Sync)

通知 RAG 服务刷新/失效指定用户的 LLM 配置缓存。通常由 Java 管理端在修改用户平台配置后触发。

*   **MQ 名称**: `tolink.rag.cache_sync`
*   **Java 生产端**: Java 端直接投递缓存同步 MQ 消息
*   **Python/RAG 消费端**: Python/RAG 订阅并消费缓存同步消息

**消息体 (Envelope JSON)**
```json
{
  "mq_type": "cache_sync",
  "mq_name": "tolink.rag.cache_sync",
  "payload": {
    "user_id": "str, 需要同步缓存的用户标识",
    "action": "str, 操作类型(refresh / invalidate / warmup)，默认为 refresh",
    "config_id": "str, 选填，具体的配置标识"
  }
}
```

**消费端逻辑说明**: 
Python 侧收到消息后，会清除内存缓存并从 Redis/MySQL 重载该用户的可用 LLM 模型、限流阈值等元数据配置。

---

### 2.4 LLM 用量上报 (Usage Report)

异步上报大模型推理调用的 Token 消耗量，由消费端落库汇总。

*   **MQ 名称**: `tolink.rag.usage_report`
*   **Java 生产端**: Java 端直接投递用量上报 MQ 消息
*   **Python/RAG 消费端**: Python/RAG 订阅并消费用量上报消息

**消息体 (Envelope JSON)**
```json
{
  "mq_type": "usage_report",
  "mq_name": "tolink.rag.usage_report",
  "payload": {
    "user_id": "str, 用户ID",
    "provider_type": "str, LLM厂商类型(如 openai, qwen, anthropic)",
    "model_name": "str, 模型名称",
    "prompt_tokens": "int, 提问消耗 Token 数，默认 0",
    "completion_tokens": "int, 回答消耗 Token 数，默认 0",
    "total_tokens": "int, 总 Token 数，默认 0"
  }
}
```

---

### 2.5 原始消息透传 (Raw Message)

原始消息透传属于调试或临时兼容能力，首期 Java 与 Python/RAG 正式业务对接不依赖该接口。正式业务消息应优先定义稳定的 MQ 名称和 Envelope payload。

如确需透传，消息体应至少包含：
```json
{
  "topic": "str, 目标 Topic 或 Queue 名称",
  "message": "str, JSON 字符串格式的消息内容",
  "key": "str, 选填，路由键 (Kafka partition key / RabbitMQ routing key)"
}
```

---

## 3. 厂商信息查询

Java 与 Python/RAG 直接连接同一个 MQ Broker 后，MQ vendor、Broker 地址、Topic/Queue 等配置由部署配置统一管理。管理端首期不通过 Python HTTP 接口查询 MQ 厂商信息。

如后续需要管理端展示 MQ 运行信息，应单独设计运维查询接口或接入监控系统，不与解析任务投递链路耦合。
