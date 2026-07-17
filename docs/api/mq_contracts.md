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
| `ChatTurnMQ` | `tolink.rag.chat_turn` | Python -> Java | 对话轮次落库（一轮问答内容，**不含 token**，不写用量账本） |
| `UsageReportMQ` | `tolink.rag.usage_report` | Python -> Java | 统一 Token 用量上报（**全部模型调用**：解析/召回侧 + 对话 generate） |
| `CacheCompensationMQ` | `tolink.cache.evict` | CDC 桥接生产 -> Java | 数据库镜像缓存补偿失效；新增统一 LLM runtime config 精确失效 target |
| Canal flatMessage（原始变更） | `tolink.canal.binlog` | Canal -> Java（CDC 桥接消费） | 行变更原始事件，桥接翻译为 `CacheCompensationMQ` |
| Canal flatMessage DLT | `<CDC source topic>.DLT` | Java -> 运维 | CDC bridge 永久失败或重试耗尽后的原始记录 |
| 缓存补偿 DLT | `tolink.cache.evict.DLT` | Java -> 运维 | 补偿消费永久失败或重试耗尽后的原始记录 |

## 契约要求

- Topic/Queue 名称变更是破坏性变更。
- 消息字段新增、删除、重命名、类型变化必须同步本文档和相关消费方。
- Java/Python 双端共享的消息必须保持幂等字段和状态字段语义稳定。
- 日志链路：跨端 trace 只走 MQ 传输 header，标准 header 为 `X-Trace-Id`；Java `MQSend` 适配层通过 `link-observability` 的 `TraceHeaders` 把当前 MDC 的 `trace_id` 自动写入 header，Java Kafka 消费入口会读取 `X-Trace-Id` / `x-trace-id` / `trace_id` / `trace-id` 并恢复到 MDC，缺失或非法时自建 trace。**trace header 不写入业务 payload，不改变消息字段契约**。

## 已下线 topic

- `tolink.rag.parse_result`：Java 侧自 LINK-165 起不再注册消费者，也不保留 `DocumentParseResultMQ` 消息模型。解析终态由 Python 写入共享数据库，前端通过 `GET /api/v1/datasets/{datasetId}/files/parse-results` 轮询 Java 查询结果。Python 停发该 topic 由 LINK-166 协调发布。

## 缓存补偿生产端（CDC 桥接）

- `tolink.cache.evict` 的生产端骨架是 CDC 桥接消费者（`CdcBridgeKafkaReceiver` / `CdcBridgeService`）：消费 Canal 原始变更 topic `tolink.canal.binlog`（flatMessage、单 topic 多表），按映射展开为 `CacheCompensationMQ` 投递。
- Canal flatMessage 关键字段：`database`、`table`、`type`(INSERT/UPDATE/DELETE)、`es`、`isDdl`、`data`（当前行数组）、`old`（UPDATE 前值；列名→值均为 String）。
- bridge 只处理配置的 `tolink.cache-consistency.cdc.database`，且只允许从当前行、old image 或声明式全局常量生成 route；禁止查询 Redis 辅助索引或回查数据库。
- 当前表映射：
  - `dataset_parse_config`：当前/旧 `dataset_id` → Java/Python 共享的 `dataset_parse_config` 原始快照缓存
  - `sys_user`：`id` → `user_profile`；UPDATE 仅变化 `last_login_at` / `last_login_time` / `updated_at` 时忽略
  - `blog_post` / `blog_asset`：全局 route `global` → `published_blog_index`
  - `llm_model_config`：INSERT/UPDATE 从 current image 取 `id`，DELETE 只从 old image 取 `id` → `llm_runtime_config`；不回查数据库或 Redis。该映射受独立 `tolink.llm-runtime-cache` readiness 门禁控制。
- `CacheCompensationMQ` 是扁平 JSON：`event_id`、`cache_target`、`route_id`、`source_table`、`operation_type`、`trace_id`、`occurred_at`。合法 target 为 `dataset_parse_config` / `user_profile` / `published_blog_index` / `llm_runtime_config`。
- `event_id` 由源 `topic:partition:offset:rowIndex:target:routeId` 派生，Kafka 重投时保持稳定；同一源事件内相同 target/route 先去重。
- bridge 对已映射坏事件（空行数组、route 缺失、未知操作）不发送补偿消息。投递 `tolink.cache.evict` 时使用 Kafka broker 确认发送，异步 producer 失败会向源 CDC consumer 抛回；发送错误退避重试，永久错误或重试耗尽后把原记录发布到 `<原 topic>.DLT`。
- 补偿消费者同样使用专用容器工厂：每条消息最大 delivery 固定为 3（首次 + 2 次重试）；坏载荷、未知 target 或删除重试耗尽都发布到 `<原 topic>.DLT`。每次失败递增 `cache_compensation_consume_failure_total`，DLT 发布成功后递增 `cache_compensation_dlt_total`；标签仅使用低基数 target/reason。补偿删除只做幂等 `fence++ + DEL data`，不写业务数据。
- DLT 发送必须取得 broker 成功结果；DLT 发送失败会继续向容器抛错，不允许源记录在没有可靠落点时结束失败处理。DLT 使用 Kafka 自选分区，原 topic、partition、offset 和异常信息由 Spring Kafka 标准 dead-letter headers 保留。
- 当前两个死信 topic 分别为 `tolink.canal.binlog.DLT`（实际名称跟随配置的 CDC source topic）与 `tolink.cache.evict.DLT`。运维确认原因并修复后，把原 payload 重新发布到对应源 topic；缓存删除和稳定 `event_id` 保证重放幂等。
- 装配开关：bridge 要求 `tolink.mq.vender=kafka` 且 `tolink.cache-consistency.cdc.enabled=true`；LLM 映射还要求全局 cache/CDC source 就绪以及 `tolink.llm-runtime-cache.enabled`、`cdc-mapping-enabled`、`consumer-targets-ready` 全部为 true。新消费者未就绪时 health reason 固定为 `LLM_RUNTIME_CONSUMER_TARGET_NOT_READY`。

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

发送时 Java MQ 适配层会随消息附带 `X-Trace-Id` header（取当前 MDC `trace_id`），Python 消费端据此绑定日志上下文；payload 仍保持上述字段，不新增 `trace_id` 字段。

阶段恢复重试约定：`is_retry=true` 时复用上一轮 `document_parsed_log` 的 `parsed_bucket_name` / `parsed_object_key` 作为本次 `md_bucket` / `md_object_key`，让 Python 从失败的后处理阶段（含稀疏向量）续跑，不重新解析原文件。Java 发送前完整性校验：`is_retry=true` 时 `previous_task_id`、`md_bucket`、`md_object_key` 必须非空，缺字段不发送。`is_retry` 由 DB 状态（`document_parse_pipeline.pipeline_status=FAILED` 且已产出 Markdown）推导，与 `trigger_mode` 解耦。

## 对话轮次落库字段（Python → Java）

`ChatTurnMQ`（topic `tolink.rag.chat_turn`，`QUEUE` 点对点）。一轮 = 「起点 `GENERATING` + 终态（`COMPLETED`/`FAILED`）」**至少两条同 `turn_id` 的消息**：Python 在生成起点发 `GENERATING`，在正常结束 / 空命中 / 生成失败 / 生成超时等终结时发 `COMPLETED` 或 `FAILED`；**空召回（0 命中）也发 `COMPLETED`**（answer 空占位），不再「不产生轮次」。routing_key = `conversation_id`，保证同一对话有序。

线格式为统一信封 `{"mq_type":"CHAT_TURN","mq_name":"tolink.rag.chat_turn","payload":{...}}`，业务字段在 `payload` 内、**全 snake_case**；Java 端 `ChatTurnMQ.parseMsg` 先解包 `payload` 再反序列化（兼容无信封扁平结构）。

`payload` 字段：

- `conversation_id`（int，必填）：所属对话。
- `turn_id`（string，必填）：**轮次幂等键**（前端每轮稳定 UUID）→ `chat_message.turn_id`，Java 据此 upsert 同一行。
- `request_id`（string，必填）：每 HTTP 请求级追踪 ID，写入 `chat_message.request_id`，**不再充当幂等键**（幂等键改用 `turn_id`）。
- `user_id`（int，必填）：用户 ID（取自 token），Java 用于归属校验。
- `query`（string）：用户提问 → `chat_message.query`。
- `answer`（string）：LLM 回答 → `chat_message.answer`（`GENERATING`/`FAILED` 可为空或半截）。
- `title`（string，可空）：Python 在首轮问答完成后生成的会话标题 → `chat_conversation.title`。Java 只在当前标题为空或仍为默认“新对话”时写入，且按 255 字符列宽截断；如果用户已手动改成其它标题则跳过，不覆盖。
- `config_id`（正整数）、`model_name`（string）：`config_id` 是 SYSTEM/USER 共用的全局配置身份。`COMPLETED` 必填；已解析到 provider/model 的 `FAILED` 必填；`GENERATING` 和模型解析前失败允许为空。
- `provider_type`（string，**可空**）：`GENERATING` 起点与模型未解析的前置失败时为空串，终态补齐。`chat_message` 无对应列，本通道不落库它。
- `references`（string[]）：召回片段 chunk_id 列表（仅标识、不含正文）→ `chat_message.references`(JSON)。
- `latency_ms`（int，可空）：生成延迟；`chat_message` 无对应列，本通道不落库它。
- `status`（string，必填）：`GENERATING` / `COMPLETED` / `FAILED`（旧 `success`/`partial`/`failed` 已退役）。
- `error_code`（string，可空）：仅 `FAILED`，`RECALL_*` 或 `GENERATION_TIMEOUT` → `chat_message.error_code`。
- `error_message`（string，可空）：仅 `FAILED`，不含堆栈 → `chat_message.error_message`。
- 信封基类自带 `message_id` / `timestamp`（仅追踪用途，不入库）。

> **LINK-191 变更**：本载荷已**移除 `prompt_tokens` / `completion_tokens` / `total_tokens`**，对话 generate 用量改由统一 Token 用量消息 `tolink.rag.usage_report`（`stage='chat'`/`operation='generate'`）承接；本通道**只持久化对话内容、不再写 `llm_usage_log`**。`provider_type` / `latency_ms` 仍保留在载荷中（供追踪），但 `chat_message` 无对应列，不落库。

Java 消费（`ChatTurnKafkaReceiver` → `ChatTurnConsumer` → `ChatTurnPersistenceService`）先从 Kafka header 恢复 `trace_id` 到 MDC，再在**单事务**内**按 `turn_id` upsert**：`GENERATING` 起点 `INSERT chat_message`（「生成中」行），终态（`COMPLETED`/`FAILED`）`UPDATE` 同一行并补齐 answer/references/模型快照/错误字段，**不写 `llm_usage_log`**（generate 用量改走 usage_report 通道）。同时 `UPDATE chat_conversation` 的 `last_config_id` / `last_model_name` / `updated_at`；若载荷带 `title`，仅在当前标题为空或仍为默认“新对话”时写入。三条必做约束：

- **按 `turn_id` upsert + 幂等**：以 `(conversation_id, turn_id)` 定位同一轮的行（`chat_message.turn_id` 已建唯一索引 `uk_chat_message_turn_id`），同一 `turn_id` 多次到达（重发/重试）不重复插入。
- **状态不回退**：终态写入后不再被迟到/重投的 `GENERATING` 覆盖；重复终态视为重投跳过。
- **归属校验**：`conversation_id` 来自前端请求体、`user_id` 取自 token，Python 仅透传不校验；Java 按 `(conversation_id, turn_id)` 匹配并校验 `conversation` 属于该 `user_id`，不匹配直接丢弃并告警，防止跨会话/跨用户写入（`turn_id` 由客户端提供且唯一索引为全局）。

`chat_message` 三个新列（`turn_id` / `error_code` / `error_message`）由 **Python migration 0023** 落库，Java 只读写行、不自行改共享库 DDL；本仓 `scripts/db/init.sql` 与 H2 `link-api/src/main/resources/schema.sql` 仅本地/测试用，与之保持字段名、索引一致。topic 由 `KafkaMQTopologyScanner` 扫描实现 `AbstractMQ` 的 `ChatTurnMQ` 自动注册创建。

## 全链路用量上报字段（Python → Java）

`UsageReportMQ`（topic `tolink.rag.usage_report`，`QUEUE` 点对点，routing_key = `user_id`）。`llm_usage_log` 已升级为「全链路模型调用账本」，本通道承载**全部模型调用用量**：解析侧 embed/vision/table、召回侧 embed/rerank，以及**对话最终 generate**（`stage='chat'`/`operation='generate'`，LINK-191 起由本通道承接，不再藏在 `chat_turn` 里）。`chat_turn` 通道只落对话内容、不再写 `llm_usage_log`。

线格式为统一信封 `{"mq_type":"USAGE_REPORT","mq_name":"tolink.rag.usage_report","payload":{...}}`，业务字段在 `payload` 内、**全 snake_case**；Java 端 `UsageReportMQ.parseMsg` 先解包 `payload` 再反序列化（兼容无信封扁平结构）。

`payload` → `llm_usage_log` 映射：

- `user_id`（string/int，必填）→ `user_id`（Python 以 string 传，Java 转 BIGINT）。
- `provider_type`（string，必填）→ `provider_type`。
- `model_name`（string，必填）→ `model_name`。
- `stage`（string，必填）→ `stage`：`parse` / `recall` / `chat`。
- `operation`（string，必填）→ `operation`：`embed` / `rerank` / `vision` / `table` / **`generate`**（对话生成；`sparse` 本期预留不上报）。Java 按 `stage`/`operation` 通用落库，无需特判 generate。
- `prompt_tokens` / `completion_tokens` / `total_tokens`（int，必填）→ 同名列；向量类（embed/rerank）`completion_tokens` 恒 0 是预期值，vision/table/generate 为真实生成 token。
- `config_id`（正整数，必填）→ `config_id`；SYSTEM 与 USER 模型调用使用同一全局身份，系统配置调用也不得为 NULL。
- `latency_ms`（int，可空）→ `latency_ms`；缺省 NULL。
- `status`（string，可空）→ `status`：`success`/`failed`，缺省补 `success`（对话 generate 端：轮次 `FAILED` 且 `total_tokens>0` → `failed`，否则 `success`；0 token 不发）。
- `task_id`（string，可空）：parse·embed 携带的解析任务锚点；当前表无独立 task 列，仅作审计锚点（日志记录）、**不落库**。
- 信封基类自带 `message_id` / `timestamp`（仅追踪用途，不入库）。

> **LINK-191 变更**：`llm_usage_log` 瘦身后已无 `conversation_id` / `message_id` / `request_id` / `fallback_config_id` 列，本载荷亦移除 `conversation_id` / `request_id`（旧上游若仍发，Java 反序列化忽略、不报错）；generate 用量行因此**无法回溯到具体对话**（有意为之）。

Java 消费链路：`UsageReportKafkaReceiver`（从 Kafka header 恢复 `trace_id`）→ `UsageReportConsumer` → `UsageReportPersistenceService`，每条上报 `INSERT llm_usage_log` 一行。

可靠性边界：

- **旁路、最终一致**：用量是事后算账的旁路记录，Python 上报失败只告警不阻断主链路，**偶发丢条可接受**，不要求强一致；全缓存命中（token=0）不上报。
- **task 级聚合**：一次解析的多个 chunk 的 embed token 合并成一条上报，不是每 chunk 一条。
- **幂等**：本通道默认 at-least-once、偶发重复可接受；未启用强去重以免与 Python 侧 schema 漂移，信封 `message_id` 仅用于排障追踪（不入库）。
- **NULL 合法态**：仅 `latency_ms` 可缺省落 NULL；`config_id` 必须是正整数。

topic 由 `KafkaMQTopologyScanner` 扫描实现 `AbstractMQ` 的 `UsageReportMQ` 自动注册创建。

> 全链路口径一致性（LINK-191）：所有模型调用（含对话 generate）的 token 均经本通道上报、落同一张 `llm_usage_log`，按 `stage` / `operation` 统一聚账；`chat_turn` 通道不再写用量账本。

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
- 发送时 Java MQ 适配层会随消息附带 `X-Trace-Id` header（取当前 MDC `trace_id`），Python 消费端据此绑定日志上下文；payload 不新增 `trace_id` 字段。
- 可靠性：Java 生产侧**尽力发**——发送失败仅告警留痕并吞掉、不影响已提交的删除，**无 DLQ、无对账兜底**（接受偶尔漏发，漏发的衍生产物为惰性垃圾、不影响活记录）。
- 幂等：Python 按 id 删天然幂等（删二次 no-op、删不存在产物 no-op），故 payload **不带去重/追踪字段**。
- Python 侧消费与删除实现在另一仓库；发布需两端协调（点对点队列，消费端就绪前 producer 不单独上生产，避免无消费者积压）。
