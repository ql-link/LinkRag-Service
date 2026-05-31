# MQ Module

## 职责

MQ 组件位于 `link-components/toLink-components-mq`，业务消息模型和消费者位于 `link-service/src/main/java/com/qingluo/link/service/mq` 以及 `link-components/.../model`。

## 当前消息

| 消息 | Topic/Queue | 方向 |
| --- | --- | --- |
| `DocumentParseTaskMQ` | `tolink.rag.parse_task` | Java -> Python |
| `DocumentParseResultMQ` | `tolink.rag.parse_result` | Python -> Java |
| `CacheCompensationMQ` | `tolink.cache.evict` | CDC/补偿 -> Java |

## 约定

- 新增消息必须实现 `AbstractMQ`。
- 消息体字段变更必须同步 `docs/reference/mq_contracts.md`。
- Kafka/RabbitMQ 差异应封装在组件适配层，不泄漏到业务 Service。
- `parse_task` 和 `parse_result` 均使用扁平 JSON；任务通过 `document_parse_file_id`、结果通过 `document_parsed_log_id` 与共享数据库记录关联。
- `parse_task` 含 `is_retry` + `previous_task_id`：重试复用上一轮 Markdown 坐标做阶段恢复，发送前完整性校验缺字段不发。端到端终态权威源为 `document_parse_pipeline.pipeline_status`（大写），结果消费状态校验与卡住扫描均改用它（原 `document_parsed_log.task_status` 已删）。
- Python 负责解析终态持久化，Java 结果消费者只进行契约校验和 SSE 事件转发。

## parse_result 消费容器工厂

- `parse_result` 单独使用专用监听容器工厂 `parseResultKafkaListenerContainerFactory`（见 `ParseResultKafkaConfig`），其余消费者（如 `CacheCompensationKafkaReceiver`）沿用 Boot 默认工厂。修改 parse_result 的错误处理不得波及缓存补偿链路。
- 专用工厂用 `SeekToCurrentErrorHandler` 替换默认零退避策略：业务不可恢复异常立即 recover，可重试异常带指数退避（最多 3 次），recover 动作为告警日志 + 监控指标，不引入 DLQ。
- 卡住扫描（`DocumentParseStuckScanner`，由 `SchedulingConfig` 开启的 `@EnableScheduling` 驱动）是对“通知丢失/未送达”的观测兜底，以共享 DB 为权威源补推或告警，不改变 MQ 投递行为。
