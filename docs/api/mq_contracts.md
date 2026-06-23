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
| `UsageReportMQ` | `tolink.rag.usage_report` | Python -> Java | 全链路用量上报（非对话调用：解析/召回侧模型用量） |
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
- `pdf_parser_backend`（可选，仅 PDF 且数据集级 `pdf_config.pdf_parser_backend` 非空时由 Java 透传）：`auto` / `mineru` / `opendataloader` / `naive`
- `is_retry`（bool，默认 `false`/省略）：Python 据此分流首次解析与阶段恢复重试
- `previous_task_id`（重试时填上一轮失败任务 `task_id`，首次解析为空）

`pdf_parser_backend` 不在 Java MQ 层补默认值；未传时应由 Python 按「数据集配置 → 环境默认 → 系统内置默认」继续兜底，避免把缺省字段伪装成显式指定。

阶段恢复重试约定：`is_retry=true` 时复用上一轮 `document_parsed_log` 的 `parsed_bucket_name` / `parsed_object_key` 作为本次 `md_bucket` / `md_object_key`，让 Python 从失败的后处理阶段（含稀疏向量）续跑，不重新解析原文件。Java 发送前完整性校验：`is_retry=true` 时 `previous_task_id`、`md_bucket`、`md_object_key` 必须非空，缺字段不发送。`is_retry` 由 DB 状态（`document_parse_pipeline.pipeline_status=FAILED` 且已产出 Markdown）推导，与 `trigger_mode` 解耦。

## 对话轮次落库字段（Python → Java）

`ChatTurnMQ`（topic `tolink.rag.chat_turn`，`QUEUE` 点对点）。一轮 = 「起点 `GENERATING` + 终态（`COMPLETED`/`FAILED`）」**至少两条同 `turn_id` 的消息**：Python 在生成起点发 `GENERATING`，在正常结束 / 空命中 / 生成失败 / 生成超时等终结时发 `COMPLETED` 或 `FAILED`；**空召回（0 命中）也发 `COMPLETED`**（answer 空占位），不再「不产生轮次」。routing_key = `conversation_id`，保证同一对话有序。

线格式为统一信封 `{"mq_type":"CHAT_TURN","mq_name":"tolink.rag.chat_turn","payload":{...}}`，业务字段在 `payload` 内、**全 snake_case**；Java 端 `ChatTurnMQ.parseMsg` 先解包 `payload` 再反序列化（兼容无信封扁平结构）。

`payload` 字段：

- `conversation_id`（int，必填）：所属对话。
- `turn_id`（string，必填）：**轮次幂等键**（前端每轮稳定 UUID）→ `chat_message.turn_id`，Java 据此 upsert 同一行。
- `request_id`（string，必填）：每 HTTP 请求级追踪 ID，写入 `chat_message.request_id` 与 `llm_usage_log.request_id`，**不再充当幂等键**（幂等键改用 `turn_id`）。
- `user_id`（int，必填）：用户 ID（取自 token），Java 用于归属校验。
- `query`（string）：用户提问 → `chat_message.query`。
- `answer`（string）：LLM 回答 → `chat_message.answer`（`GENERATING`/`FAILED` 可为空或半截）。
- `config_id`（int）、`model_name`（string）：本轮配置与模型快照（`GENERATING` 起点可能未解析）。
- `provider_type`（string，**可空**）：`GENERATING` 起点与模型未解析的前置失败时为空串，终态补齐。
- `prompt_tokens` / `completion_tokens` / `total_tokens`（int，流式未返回时为 0）。
- `references`（string[]）：召回片段 chunk_id 列表（仅标识、不含正文）→ `chat_message.references`(JSON)。
- `latency_ms`（int，可空）：生成延迟。
- `status`（string，必填）：`GENERATING` / `COMPLETED` / `FAILED`（旧 `success`/`partial`/`failed` 已退役）。
- `error_code`（string，可空）：仅 `FAILED`，`RECALL_*` 或 `GENERATION_TIMEOUT` → `chat_message.error_code`。
- `error_message`（string，可空）：仅 `FAILED`，不含堆栈 → `chat_message.error_message`。
- 信封基类自带 `message_id` / `timestamp`（仅追踪用途，不入库）。

Java 消费（`ChatTurnKafkaReceiver` → `ChatTurnConsumer` → `ChatTurnPersistenceService`）在**单事务**内**按 `turn_id` upsert**：`GENERATING` 起点 `INSERT chat_message`（「生成中」行），终态（`COMPLETED`/`FAILED`）`UPDATE` 同一行并补齐 answer/references/模型快照/错误字段。`INSERT llm_usage_log`（关联 `conversation_id` / `message_id` / `request_id`）**仅在转入终态时写一次**（`GENERATING` 无 token，不入账，避免重复计费；账本 `status` 沿用 `success`/`failed`：`COMPLETED`→`success`、`FAILED`→`failed`）。同时 `UPDATE chat_conversation` 的 `last_config_id` / `last_model_name` / `updated_at`，并在首轮由 `query` 生成临时标题；终态成功（`COMPLETED`）时异步调用用户 Chat 模型生成自然短标题，失败、拒绝或配置不可用时保留临时标题。三条必做约束：

- **按 `turn_id` upsert + 幂等**：以 `(conversation_id, turn_id)` 定位同一轮的行（`chat_message.turn_id` 已建唯一索引 `uk_chat_message_turn_id`），同一 `turn_id` 多次到达（重发/重试）不重复插入。
- **状态不回退**：终态写入后不再被迟到/重投的 `GENERATING` 覆盖；重复终态视为重投跳过。
- **归属校验**：`conversation_id` 来自前端请求体、`user_id` 取自 token，Python 仅透传不校验；Java 按 `(conversation_id, turn_id)` 匹配并校验 `conversation` 属于该 `user_id`，不匹配直接丢弃并告警，防止跨会话/跨用户写入（`turn_id` 由客户端提供且唯一索引为全局）。

`chat_message` 三个新列（`turn_id` / `error_code` / `error_message`）由 **Python migration 0023** 落库，Java 只读写行、不自行改共享库 DDL；本仓 `scripts/db/init.sql` 与 H2 `link-api/src/main/resources/schema.sql` 仅本地/测试用，与之保持字段名、索引一致。topic 由 `KafkaMQTopologyScanner` 扫描实现 `AbstractMQ` 的 `ChatTurnMQ` 自动注册创建。

## 全链路用量上报字段（Python → Java）

`UsageReportMQ`（topic `tolink.rag.usage_report`，`QUEUE` 点对点，routing_key = `user_id`）。`llm_usage_log` 已升级为「全链路模型调用账本」，本通道承载**非对话调用**：解析侧 embed/vision/table、召回侧 embed/rerank；对话最终 generate 走 `ChatTurnMQ` 通道，两条通道落同一张 `llm_usage_log`、口径一致。

线格式为统一信封 `{"mq_type":"USAGE_REPORT","mq_name":"tolink.rag.usage_report","payload":{...}}`，业务字段在 `payload` 内、**全 snake_case**；Java 端 `UsageReportMQ.parseMsg` 先解包 `payload` 再反序列化（兼容无信封扁平结构）。

`payload` → `llm_usage_log` 映射：

- `user_id`（string/int，必填）→ `user_id`（Python 以 string 传，Java 转 BIGINT）。
- `provider_type`（string，必填）→ `provider_type`。
- `model_name`（string，必填）→ `model_name`。
- `stage`（string，必填）→ `stage`：`parse` / `recall` / `chat`。
- `operation`（string，必填）→ `operation`：`embed` / `rerank` / `vision` / `table`（`generate` 走 chat_turn 通道补；`sparse` 本期预留不上报）。
- `prompt_tokens` / `completion_tokens` / `total_tokens`（int，必填）→ 同名列；向量类（embed/rerank）`completion_tokens` 恒 0 是预期值，vision/table 为真实生成 token。
- `config_id`（int，可空）→ `config_id`；系统配置调用（如召回 query 编码）缺省 → **NULL**。
- `conversation_id`（int，可空）→ `conversation_id`；缺省 NULL。
- `request_id`（string，可空）→ `request_id`；串联同一次召回多条用量，召回侧当前暂不透传 → NULL。
- `latency_ms`（int，可空）→ `latency_ms`；缺省 NULL。
- `status`（string，可空）→ `status`：`success`/`partial`/`failed`，缺省补 `success`。
- `task_id`（string，可空）：parse·embed 携带的解析任务锚点；当前表无独立 task 列，仅作审计锚点（日志记录）、**不落库**。
- 信封基类自带 `message_id` / `timestamp`（仅追踪用途，不入库）。

Java 消费链路：`UsageReportKafkaReceiver` → `UsageReportConsumer` → `UsageReportPersistenceService`，每条上报 `INSERT llm_usage_log` 一行。

可靠性边界：

- **旁路、最终一致**：用量是事后算账的旁路记录，Python 上报失败只告警不阻断主链路，**偶发丢条可接受**，不要求强一致；全缓存命中（token=0）不上报。
- **task 级聚合**：一次解析的多个 chunk 的 embed token 合并成一条上报，不是每 chunk 一条。
- **幂等**：本通道默认 at-least-once、偶发重复可接受；未启用强去重以免与 Python 侧 schema 漂移，信封 `message_id` 仅用于排障追踪（不入库）。
- **NULL 合法态**：`config_id`/`conversation_id`/`request_id`/`latency_ms` 缺省即落 NULL，不补默认值。

topic 由 `KafkaMQTopologyScanner` 扫描实现 `AbstractMQ` 的 `UsageReportMQ` 自动注册创建。

> 全链路口径一致性（LINK-184 §8）：`chat_turn` 落库写 `llm_usage_log` 时由 Java 自行补 `stage='chat'` / `operation='generate'`，使两条通道在 `stage` / `operation` 维度可统一聚账。

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
