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
| `ChatTurnMQ` | `tolink.rag.chat_turn` | Python -> Java | 对话轮次落库（一轮问答完整数据） |
| `CacheCompensationMQ` | `tolink.cache.evict` | CDC 桥接生产 -> Java | 缓存补偿删除（生产端为 CDC 桥接消费者，见下） |
| Canal flatMessage（原始变更） | `tolink.canal.binlog` | Canal -> Java（CDC 桥接消费） | 行变更原始事件，桥接翻译为 `CacheCompensationMQ` |

## 契约要求

- Topic/Queue 名称变更是破坏性变更。
- 消息字段新增、删除、重命名、类型变化必须同步本文档和相关消费方。
- Java/Python 双端共享的消息必须保持幂等字段和状态字段语义稳定。
- 日志链路：Java 消费入口按消息自建 traceId 仅用于本端日志串联，**不写入消息体、不属于消息契约**；消息字段不变。

## 已下线 topic

- `tolink.rag.parse_result`：Java 侧自 LINK-165 起不再注册消费者，也不保留 `DocumentParseResultMQ` 消息模型。解析终态由 Python 写入共享数据库，前端通过 `GET /api/v1/datasets/{datasetId}/files/parse-results` 轮询 Java 查询结果。Python 停发该 topic 由 LINK-166 协调发布。

## 缓存补偿生产端（CDC 桥接）

- `tolink.cache.evict` 的生产端是 CDC 桥接消费者（`CdcBridgeKafkaReceiver` / `CdcBridgeService`）：消费 Canal 原始变更 topic `tolink.canal.binlog`（flatMessage、单 topic 多表），按统一映射展开为 `CacheCompensationMQ` 投递；既有消费端链路不变。
- Canal flatMessage 关键字段：`table`、`type`(INSERT/UPDATE/DELETE)、`es`、`isDdl`、`data`（变更行数组，列名→值均为 String，DELETE 为 before image）。
- 统一映射（表 → [(缓存目标, route_id 取法)]）：`sys_user`→USER(id)；`llm_user_config`→LLM_CONFIG(id) + USER_DEFAULT_LLM_CONFIG(user_id)；`llm_system_provider`→SYSTEM_PROVIDER(provider_type)；`llm_provider_model`→SYSTEM_PROVIDER（provider_id 经厂商索引缓存换 provider_type，查不到则降级跳过）。
- 失败分类（专用容器工厂 `cdcBridgeKafkaListenerContainerFactory`）：坏消息（IllegalArgumentException/DeserializationException）立即跳过 + 告警 + 指标；暂时错误退避重试（最多 3 次）耗尽后跳过；不引入 DLQ。
- 装配开关：`tolink.cache-consistency.cdc.enabled`（默认 false）且 vender=kafka 二者皆满足才装载；消费者与专用容器工厂共用同一条件，不会出现 vender=kafka 但开关关闭时容器工厂仍被创建的“半开”状态。本地/测试零报错。

## 解析消息字段

`DocumentParseTaskMQ` 使用扁平 JSON，字段为：

- `task_id`、`original_file_id`、`document_parse_file_id`、`user_id`、`dataset_id`
- `trigger_mode`：`upload_auto` 或 `manual_retry`
- `file_type`、`source_bucket`、`source_object_key`、`source_filename`
- `md_bucket`、`md_object_key`
- `is_retry`（bool，默认 `false`/省略）：Python 据此分流首次解析与阶段恢复重试
- `previous_task_id`（重试时填上一轮失败任务 `task_id`，首次解析为空）

阶段恢复重试约定：`is_retry=true` 时复用上一轮 `document_parsed_log` 的 `parsed_bucket_name` / `parsed_object_key` 作为本次 `md_bucket` / `md_object_key`，让 Python 从失败的后处理阶段（含稀疏向量）续跑，不重新解析原文件。Java 发送前完整性校验：`is_retry=true` 时 `previous_task_id`、`md_bucket`、`md_object_key` 必须非空，缺字段不发送。`is_retry` 由 DB 状态（`document_parse_pipeline.pipeline_status=FAILED` 且已产出 Markdown）推导，与 `trigger_mode` 解耦。

## 对话轮次落库字段（Python → Java）

`ChatTurnMQ`（topic `tolink.rag.chat_turn`，`QUEUE` 点对点）。Python 问答执行器在一轮问答**正常结束 / 生成失败 / 客户端断连**时各发一条，**空召回不发**；routing_key = `conversation_id`，保证同一对话有序。

线格式为统一信封 `{"mq_type":"CHAT_TURN","mq_name":"tolink.rag.chat_turn","payload":{...}}`，业务字段在 `payload` 内、**全 snake_case**；Java 端 `ChatTurnMQ.parseMsg` 先解包 `payload` 再反序列化（兼容无信封扁平结构）。

`payload` 字段：

- `conversation_id`（int，必填）：所属对话。
- `request_id`（string，必填）：幂等键，写入 `chat_message.request_id` 与 `llm_usage_log.request_id`。
- `user_id`（int，必填）：用户 ID（取自 token），Java 用于归属校验。
- `query`（string）：用户提问 → `chat_message.query`。
- `answer`（string）：LLM 回答 → `chat_message.answer`（partial 为半截，failed 可空串）。
- `config_id`（int）、`provider_type`（string）、`model_name`（string）：本轮配置与模型快照。
- `prompt_tokens` / `completion_tokens` / `total_tokens`（int，流式未返回时为 0）。
- `references`（string[]）：召回片段 chunk_id 列表（仅标识、不含正文）→ `chat_message.references`(JSON)。
- `latency_ms`（int，可空）：生成延迟。
- `status`（string，必填）：`success` / `partial`（客户端断连）/ `failed`（生成异常）。
- 信封基类自带 `message_id` / `timestamp`（仅追踪用途，不入库）。

Java 消费（`ChatTurnKafkaReceiver` → `ChatTurnConsumer` → `ChatTurnPersistenceService`）在**单事务**内：① `INSERT chat_message`（一行一轮）；② `INSERT llm_usage_log`（关联 `conversation_id` / `message_id` / `request_id`）；③ `UPDATE chat_conversation` 的 `last_config_id` / `last_model_name` / `updated_at`，并在首轮由 `query` 生成标题。两条必做约束：

- **幂等去重**：以 `request_id` 在落库前做存在性校验，命中则跳过，应对 MQ 重投（同对话单分区有序，重投为顺序到达，存在性校验即可去重；不依赖 DB 唯一索引，避免与 Python 侧迁移产生 schema 漂移）。
- **归属校验**：`conversation_id` 来自前端请求体、`user_id` 取自 token，Python 仅透传不校验；Java 落库前必须校验 `conversation` 属于该 `user_id`，不匹配直接丢弃并告警，防止跨用户写入。

topic 由 `KafkaMQTopologyScanner` 扫描实现 `AbstractMQ` 的 `ChatTurnMQ` 自动注册创建。发布顺序：Python 迁移 0021 → Python 发送侧 → Java 消费侧 + topic 注册，避免消息长时间堆积无人消费。

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
