# MQ Contracts

MQ 实现事实来源：

- `link-components/toLink-components-mq`
- `link-service/src/main/java/com/qingluo/link/service/mq`
- `link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/model`

## 消息清单

| 消息模型 | Topic/Queue | 方向 | 说明 |
| --- | --- | --- | --- |
| `DocumentParseTaskMQ` | `tolink.rag.parse_task` | Java -> Python | 文档解析任务 |
| `DocumentParseResultMQ` | `tolink.rag.parse_result` | Python -> Java | 解析终态结果 |
| `RagCacheSyncMQ` | `tolink.rag.cache_sync` | Java -> Python | 用户 LLM 配置缓存同步 |
| `CacheCompensationMQ` | `tolink.cache.evict` | 补偿生产者 -> Java | 缓存补偿删除 |

## 契约要求

- Topic/Queue 名称变更是破坏性变更。
- 消息字段新增、删除、重命名、类型变化必须同步本文档和相关消费方。
- Java/Python 双端共享的消息必须保持幂等字段和状态字段语义稳定。

## 解析消息字段

`DocumentParseTaskMQ` 使用扁平 JSON，字段为：

- `task_id`、`original_file_id`、`document_parse_file_id`、`user_id`、`dataset_id`
- `trigger_mode`：`upload_auto` 或 `manual_retry`
- `file_type`、`source_bucket`、`source_object_key`、`source_filename`
- `md_bucket`、`md_object_key`
- `is_retry`（bool，默认 `false`/省略）：Python 据此分流首次解析与阶段恢复重试
- `previous_task_id`（重试时填上一轮失败任务 `task_id`，首次解析为空）

阶段恢复重试约定：`is_retry=true` 时复用上一轮 `document_parsed_log` 的 `parsed_bucket_name` / `parsed_object_key` 作为本次 `md_bucket` / `md_object_key`，让 Python 从失败的后处理阶段（含稀疏向量）续跑，不重新解析原文件。Java 发送前完整性校验：`is_retry=true` 时 `previous_task_id`、`md_bucket`、`md_object_key` 必须非空，缺字段不发送。`is_retry` 由 DB 状态（`document_parse_pipeline.pipeline_status=FAILED` 且已产出 Markdown）推导，与 `trigger_mode` 解耦。

`DocumentParseResultMQ` 使用扁平 JSON，字段为：

- `task_id`、`original_file_id`、`document_parsed_log_id`、`dataset_id`、`user_id`
- `task_status`：仅 `success` 或 `failed`
- `failure_reason`：失败必填，成功必须为空
- `parse_finished_at`

Python 在发送结果前先写入 `document_parsed_log` 与 `document_parse_file`；Java 消费结果只校验归属并转发 SSE，不回写终态。

## LLM 配置缓存同步消息

`RagCacheSyncMQ` 使用扁平 JSON，字段为：

- `user_id`：必填，变更配置所属用户。
- `config_id`：配置 ID；`invalidate` 时必填。
- `action`：`refresh` 或 `invalidate`。

发送时机：Java 用户 LLM 配置新增、更新、设置默认后，在数据库事务 `afterCommit` 发送 `refresh`；删除后发送 `invalidate`。Python RAG 端消费 `tolink.rag.cache_sync` 后清理该用户配置缓存和 ModelFactory 客户端缓存。该消息不携带 API Key 或明文敏感信息。

`user_id=0` 是系统预设配置的缓存同步信号，不代表真实用户。Python 收到 `user_id=0 + action=refresh` 时应清理全量 LLM 配置缓存、系统 provider 缓存和全部 ModelFactory client 缓存，因为任意用户都可能在没有个人默认配置时回退到系统预设。

## parse_result 消费接收兜底

消息体不变，仅强化 Java 消费侧的接收健壮性：

- **专用容器工厂**：`parse_result` 使用专用 `ConcurrentKafkaListenerContainerFactory`（bean `parseResultKafkaListenerContainerFactory`）+ `SeekToCurrentErrorHandler`；`tolink.cache.evict` 等其他消费者仍走 Boot 默认工厂，互不影响。
- **失败分类**：`task_id`/状态/归属不匹配（`NonRetryableParseResultException`）与坏 JSON（`IllegalArgumentException`/`DeserializationException`）判为不可重试，立即告警+提交跳过；`document_parsed_log` 暂不存在（`ParseResultPendingException`）与基础设施异常带退避重试（指数退避，最多 3 次，1s→×2→上限 10s），耗尽后告警+跳过。
- **无 DLQ**：失败兜底为告警日志 + 监控指标（`tolink.parse_result.recover` 等），不静默丢弃、不引入死信队列。
- **当前任务过滤**：结果消费与卡住补推前比对消息 `task_id` 与 `document_parse_file.latest_parse_task_id`，旧任务/乱序结果不推终态 SSE，指针为空时 fail-open 放行。
- **卡住兜底**：定时扫描 `document_parse_pipeline` 中仍为 `PENDING`/`PROCESSING`（运行中）且超阈值的当前任务，重读 DB——已终态（`SUCCESS`/`FAILED`）则以 DB 为准补推一次终态 SSE，仍运行中则告警。Java 全程只读，不回写终态。
- **终态判定迁移**：结果消费的状态一致性校验与卡住扫描原依据 `document_parsed_log.task_status`（已删除），现统一改用 `document_parse_pipeline.pipeline_status`（大写）。
