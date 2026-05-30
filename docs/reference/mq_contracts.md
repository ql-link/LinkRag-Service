# MQ Contracts

MQ 实现事实来源：

- `link-components/toLink-components-mq`
- `link-service/src/main/java/com/qingluo/link/service/mq`
- `link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/model`

## 消息清单

| 消息模型 | Topic/Queue | 方向 | 说明 |
| --- | --- | --- | --- |
| `DocumentParseTaskMQ` | `tolink.rag.parse_task` | Java -> Python | 文档解析任务 |
| `DocumentDeleteNotifyMQ` | `tolink.rag.document_delete` | Java -> Python | 删除通知（通知 Python 删衍生产物） |
| `DocumentParseResultMQ` | `tolink.rag.parse_result` | Python -> Java | 解析终态结果 |
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

## 删除通知字段

`DocumentDeleteNotifyMQ`（topic `tolink.rag.document_delete`，`QUEUE` 点对点）使用扁平 JSON，按删除范围 `delete_type` 分流：

- `delete_type`：`dataset` 或 `file`（范围判别）。
- `dataset_id`、`user_id`：两种范围都必填。
- `original_file_id`：仅 `delete_type=file` 必填；`dataset` 范围不下发（值为 null，fastjson 省略该键）。

样例：

- dataset：`{"delete_type":"dataset","dataset_id":10,"user_id":100}`
- file：`{"delete_type":"file","dataset_id":200,"user_id":100,"original_file_id":1}`

语义与约定：

- **删数据集**发 `dataset` 范围（仅 `dataset_id`）：Python 按 `dataset_id` 删该数据集名下全部衍生产物（`document_parse_file` / `document_parsed_log` 行 + OSS 清洗文件/Markdown/向量）；删超大数据集消息体恒定，不下发文件 id 列表。
- **删文件**发 `file` 范围（`original_file_id`）：Python 按该 id 删对应衍生产物。
- 发送时机：删除事务 afterCommit（回滚不发）。发送前完整性校验：`delete_type` 合法、`dataset_id`/`user_id` 非空、`file` 范围 `original_file_id` 非空，缺字段不投递。
- 可靠性：Java 生产侧**尽力发**——发送失败仅告警留痕并吞掉、不影响已提交的删除，**无 DLQ、无对账兜底**（接受偶尔漏发，漏发的衍生产物为惰性垃圾、不影响活记录）。
- 幂等：Python 按 id 删天然幂等（删二次 no-op、删不存在产物 no-op），故消息**不带去重/追踪字段**。
- Python 侧消费与删除实现在另一仓库；发布需两端协调（点对点队列，消费端就绪前 producer 不单独上生产，避免无消费者积压）。

## parse_result 消费接收兜底

消息体不变，仅强化 Java 消费侧的接收健壮性（详见 `docs/parse-result-consumer-resilience/`）：

- **专用容器工厂**：`parse_result` 使用专用 `ConcurrentKafkaListenerContainerFactory`（bean `parseResultKafkaListenerContainerFactory`）+ `SeekToCurrentErrorHandler`；`tolink.cache.evict` 等其他消费者仍走 Boot 默认工厂，互不影响。
- **失败分类**：`task_id`/状态/归属不匹配（`NonRetryableParseResultException`）与坏 JSON（`IllegalArgumentException`/`DeserializationException`）判为不可重试，立即告警+提交跳过；`document_parsed_log` 暂不存在（`ParseResultPendingException`）与基础设施异常带退避重试（指数退避，最多 3 次，1s→×2→上限 10s），耗尽后告警+跳过。
- **无 DLQ**：失败兜底为告警日志 + 监控指标（`tolink.parse_result.recover` 等），不静默丢弃、不引入死信队列。
- **当前任务过滤**：结果消费与卡住补推前比对消息 `task_id` 与 `document_parse_file.latest_parse_task_id`，旧任务/乱序结果不推终态 SSE，指针为空时 fail-open 放行。
- **卡住兜底**：定时扫描 `document_parse_pipeline` 中仍为 `PENDING`/`PROCESSING`（运行中）且超阈值的当前任务，重读 DB——已终态（`SUCCESS`/`FAILED`）则以 DB 为准补推一次终态 SSE，仍运行中则告警。Java 全程只读，不回写终态。
- **终态判定迁移**：结果消费的状态一致性校验与卡住扫描原依据 `document_parsed_log.task_status`（已删除），现统一改用 `document_parse_pipeline.pipeline_status`（大写）。
