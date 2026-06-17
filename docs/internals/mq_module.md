# MQ Module

## 职责

MQ 组件位于 `link-components/toLink-components-mq`，业务消息模型和消费者位于 `link-service/src/main/java/com/qingluo/link/service/mq` 以及 `link-components/.../model`。

## 当前消息

| 消息 | Topic/Queue | 方向 |
| --- | --- | --- |
| `DocumentParseTaskMQ` | `tolink.rag.parse_task` | Java -> Python |
| `DocumentDeleteNotifyMQ` | `tolink.rag.document_delete` | Java -> Python |
| `CacheCompensationMQ` | `tolink.cache.evict` | CDC 桥接生产 -> Java |
| Canal flatMessage | `tolink.canal.binlog` | Canal -> Java（CDC 桥接消费） |

## 约定

- 新增消息必须实现 `AbstractMQ`。
- 消息体字段变更必须同步 `docs/api/mq_contracts.md`。
- Kafka/RabbitMQ 差异应封装在组件适配层，不泄漏到业务 Service。
- `parse_task` 使用扁平 JSON，通过 `document_parse_file_id` 与共享数据库记录关联。
- `parse_task` 含 `is_retry` + `previous_task_id`：重试复用上一轮 Markdown 坐标做阶段恢复，发送前完整性校验缺字段不发。端到端终态权威源为 `document_parse_pipeline.pipeline_status`（大写），Java 解析结果查询以 DB 为准（原 `document_parsed_log.task_status` 已删）。
- `document_delete`（删除通知，Java -> Python）：删除事务 afterCommit **真实投递**（回滚不发），`QUEUE` 点对点；按删除范围分流——删数据集传 `dataset_id`、删文件传 `original_file_id`，`delete_type` 判别，发送前完整性校验缺字段不发。生产侧**尽力发**：`DocumentDeleteNotifier` 对发送失败/发送器缺失仅告警留痕并吞掉、不外抛、不影响已提交的删除，无 DLQ、无对账兜底；幂等由 Python 按 id 删天然保证（不带去重/追踪字段）。
- Python 负责解析终态持久化；Java 不再消费 `tolink.rag.parse_result`，前端终态展示通过 `parse-results` 查询接口读取 DB。
- 消费入口（如 `CacheCompensationKafkaReceiver`）在 `receive` 起始用 `TraceContext.startNew()` 自建 traceId、`finally` 清理，使每条消息的处理日志可独立串联（不改消息体契约，仅日志关联）。
- `tolink.rag.parse_result` 已下线 Java 消费方，相关 listener、专用容器工厂、消息模型、异常分类、卡住扫描和指标均已删除。Python 停发由 LINK-166 协调发布。

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
