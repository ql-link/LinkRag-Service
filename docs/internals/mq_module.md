# MQ Module

## 职责

MQ 组件位于 `link-components/toLink-components-mq`，业务消息模型和消费者位于 `link-service/src/main/java/com/qingluo/link/service/mq` 以及 `link-components/.../model`。

## 当前消息

| 消息 | Topic/Queue | 方向 |
| --- | --- | --- |
| `DocumentParseTaskMQ` | `tolink.rag.parse_task` | Java -> Python |
| `DocumentDeleteNotifyMQ` | `tolink.rag.document_delete` | Java -> Python |
| `ChatTurnMQ` | `tolink.rag.chat_turn` | Python -> Java |
| `UsageReportMQ` | `tolink.rag.usage_report` | Python -> Java |
| `CacheCompensationMQ` | `tolink.cache.evict` | CDC 桥接生产 -> Java |
| Canal flatMessage | `tolink.canal.binlog` | Canal -> Java（CDC 桥接消费） |

## 约定

- 新增消息必须实现 `AbstractMQ`。
- 消息体字段变更必须同步 `docs/api/mq_contracts.md`。
- Kafka/RabbitMQ 差异应封装在组件适配层，不泄漏到业务 Service。
- `parse_task` 使用扁平 JSON，通过 `document_parse_file_id` 与共享数据库记录关联。
- `parse_task` 对 PDF 文件可透传数据集级 `pdf_config.pdf_parser_backend` 为 `pdf_parser_backend`；Java 只在配置非空且值合法时写入，不在 MQ 层补默认值。
- `parse_task` 含 `is_retry` + `previous_task_id`：重试复用上一轮 Markdown 坐标做阶段恢复，发送前完整性校验缺字段不发。端到端终态权威源为 `document_parse_pipeline.pipeline_status`（大写），Java 解析结果查询以 DB 为准（原 `document_parsed_log.task_status` 已删）。
- `document_delete`（删除通知，Java -> Python）：删除事务 afterCommit **真实投递**（回滚不发），`QUEUE` 点对点；按删除范围分流——删数据集传 `dataset_id`、删文件传 `original_file_id`，`delete_type` 判别，发送前完整性校验缺字段不发。生产侧**尽力发**：`DocumentDeleteNotifier` 对发送失败/发送器缺失仅告警留痕并吞掉、不外抛、不影响已提交的删除，无 DLQ、无对账兜底；幂等由 Python 按 id 删天然保证（不带去重/追踪字段）。
- Python 负责解析终态持久化；Java 不再消费 `tolink.rag.parse_result`，前端终态展示通过 `parse-results` 查询接口读取 DB。
- 消费入口（如 `ChatTurnKafkaReceiver`、`CacheCompensationKafkaReceiver`）在 `receive` 起始用 `TraceContext.startNew()` 自建 traceId、`finally` 清理，使每条消息的处理日志可独立串联（不改消息体契约，仅日志关联）。
- `tolink.rag.parse_result` 已下线 Java 消费方，相关 listener、专用容器工厂、消息模型、异常分类、卡住扫描和指标均已删除。Python 停发由 LINK-166 协调发布。

## chat_turn 对话轮次落库（Python -> Java）

- `ChatTurnMQ`（`tolink.rag.chat_turn`，`QUEUE`，routing_key = `conversation_id`）线格式为统一信封 `{"mq_type","mq_name","payload":{...}}`，业务字段在 `payload` 内、全 snake_case；`ChatTurnMQ.parseMsg` 先解包 `payload` 再反序列化（兼容无信封扁平结构），与 `parse_task` 的纯扁平 JSON 不同。
- 一轮 = 「起点 `GENERATING` + 终态（`COMPLETED`/`FAILED`）」至少两条同 `turn_id` 的消息（旧 `success`/`partial`/`failed` 已退役）；空召回也发 `COMPLETED` 占位。
- 链路：`ChatTurnKafkaReceiver`（薄适配层 + traceId）→ `ChatTurnConsumer`（实现 `ChatTurnMQ.MQReceiver`）→ `ChatTurnPersistenceService`（`@Transactional` 单事务）。
- 单事务按 `turn_id` upsert：`GENERATING` 起点 `INSERT chat_message`（「生成中」行），终态 `UPDATE` 同一行补齐 answer/references/模型快照/错误字段；**不写 `llm_usage_log`**（LINK-191：generate 用量改走 usage_report 通道，本通道只落对话内容；`provider_type`/`latency_ms` 仍在载荷但 `chat_message` 无对应列、不落库）；`UPDATE chat_conversation` 的 `last_config_id` / `last_model_name` / `updated_at`，首轮由 `query` 生成临时标题。事务提交后，Java 通过 `conversationTitleExecutor` 在终态成功（`COMPLETED`）时异步调用用户本轮 Chat 配置（不可用则回退默认 CHAT 配置）的 OpenAI-compatible chat completions 生成自然短标题；失败、拒绝、配置不可用或用户已手动改成其它标题时保留当前标题。
- 按 `turn_id` 幂等 + 状态不回退：以 `(conversation_id, turn_id)` 定位同一行（`uk_chat_message_turn_id` 唯一索引），同 `turn_id` 重投不重复插入，终态写入后不被迟到/重投的 `GENERATING` 覆盖、重复终态跳过；归属校验要求 `conversation` 属于 payload `user_id`，按 `(conversation_id, turn_id)` 匹配防跨会话/跨用户写入，不匹配丢弃 + 告警。
- topic 由 `KafkaMQTopologyScanner` 扫描实现 `AbstractMQ` 的 `ChatTurnMQ` 自动注册创建；当前沿用 Boot 默认监听容器工厂。

## usage_report 全链路用量上报（Python -> Java）

- `UsageReportMQ`（`tolink.rag.usage_report`，`QUEUE`，routing_key = `user_id`）线格式同为统一信封 `{"mq_type","mq_name","payload":{...}}`，业务字段全 snake_case；`UsageReportMQ.parseMsg` 先解包 `payload` 再反序列化（兼容无信封扁平结构），并校验 `stage`/`operation` 枚举与必填 token。
- 链路：`UsageReportKafkaReceiver`（薄适配层 + traceId）→ `UsageReportConsumer`（实现 `UsageReportMQ.MQReceiver`）→ `UsageReportPersistenceService`，每条上报 `INSERT llm_usage_log` 一行。
- 承载**全部模型调用**用量（解析侧 embed/vision/table、召回侧 embed/rerank，以及对话 generate `stage='chat'`/`operation='generate'`）；自 LINK-191 起 generate 用量统一经本通道落 `llm_usage_log`，`chat_turn` 通道不再写本表。Java 按 `stage`/`operation` 通用落库，无需特判 generate。
- 旁路、最终一致：偶发丢条/重复可接受，未启用强去重（信封 `message_id` 仅排障）；`config_id`/`latency_ms` 缺省落 NULL；`task_id` 当前无独立列，仅日志审计不落库。瘦身后 `llm_usage_log` 已无 `conversation_id`/`message_id`/`request_id`/`fallback_config_id` 列，Java 落库不再写这些字段（载荷亦不再带 `conversation_id`/`request_id`，旧上游若带则忽略）。
- topic 由 `KafkaMQTopologyScanner` 扫描实现 `AbstractMQ` 的 `UsageReportMQ` 自动注册创建。

## CDC 桥接生产端（缓存补偿）

- `tolink.cache.evict` 的生产端：`CdcBridgeKafkaReceiver`（监听 Canal 原始 topic `tolink.canal.binlog`）→ `CdcBridgeService`（统一映射展开）→ 复用 `MQSend` 投递 `CacheCompensationMQ`，不直接删缓存（保持 `tolink.cache.evict` 为唯一补偿入口）。
- 统一映射声明在 `CdcCacheEvictMapping`：表 → [(缓存目标, route_id 取法)]，取法分直取（读本行字段）与解析（`llm_provider_model` 的 provider_id 经厂商索引缓存换 provider_type，查不到降级跳过），展开循环零分支。
- 专用容器工厂 `cdcBridgeKafkaListenerContainerFactory`（`CdcBridgeKafkaConfig`）：坏消息（IllegalArgumentException/DeserializationException）判不可重试立即 recover；其余退避重试（最多 3 次）耗尽 recover；recover = 告警 + `CdcBridgeMetrics` 指标 + 跳过，不引入 DLQ。
- 装配：消费者 `CdcBridgeKafkaReceiver` 与容器工厂 `CdcBridgeKafkaConfig` **共用同一 `@ConditionalOnExpression` 条件**（抽为常量 `CdcBridgeKafkaConfig.CDC_BRIDGE_CONDITION`）——vender=kafka 且 `tolink.cache-consistency.cdc.enabled=true`（默认 false）二者皆满足才装载。两者口径一致，杜绝 vender=kafka 但 CDC 关闭时仍创建空转容器工厂的“半开”状态；CDC 未部署环境零报错。开关在 `application.yml` 已显式声明 `cdc.enabled: false`。
- 隐式假设：桥接按 `table` 名分流、**不校验 `database`**，依赖 Canal 实例只订阅业务库目标表（见 brief 3.1）。多库部署若出现同名表需在 Canal 侧隔离订阅范围。

### 新增一张缓存补偿表的步骤

按 route_id 能否从变更行直接取到，改动量递增（理想情况只改第 1～2 步）：

1. **Canal 订阅该表**（运维侧）：在 Canal 实例订阅清单加入该表，否则桥接收不到其变更。
2. **映射表加一行**（`CdcCacheEvictMapping` 的 `rules`）：`表名 → [(缓存目标, 取法)]`。
   - route_id 就在变更行某字段：用 `direct("字段名")`（字段缺失会按坏消息抛出）。
   - route_id 需跨表 / 查缓存换算：写解析取法 `row -> resolveXxx(row.get("外键"))`，查不到返回 `null` 即降级跳过（如 `llm_provider_model` 经厂商索引缓存换 `provider_type`）。
   - 一张表删多类缓存：在该行挂多个 `MappingRule`，展开循环自然跑多遍。
3. **若是全新缓存类型**（复用已有 `CacheEvictTarget` 可跳过本步）：
   - `CacheEvictTarget` 加枚举值（`code` 唯一）；
   - `CacheKeyRouter.route` 为该目标补「删哪些 Redis key」。
4. **补测试**：`CdcBridgeServiceTest` 加该表的展开用例；全新缓存类型在缓存路由/一致性测试补 key 断言。
5. **同步文档**：本文件与 `docs/api/mq_contracts.md` 的映射清单。

核心不变式：每张被监听表的 route_id 都应能由「本行字段 + 至多一次字典查询」确定。满足即进映射表走统一展开循环（**零分支、不改 `CdcBridgeService`**）；不满足才需写自定义解析取法。
