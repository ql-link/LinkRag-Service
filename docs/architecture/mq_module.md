# MQ Module

## 职责

MQ 组件位于 `link-components/toLink-components-mq`，业务消息模型和消费者位于 `link-service/src/main/java/com/qingluo/link/service/mq` 以及 `link-components/.../model`。

## 当前消息

| 消息 | Topic/Queue | 方向 |
| --- | --- | --- |
| `KnowledgeParseTaskMQ` | `tolink.rag.parse_task` | Java -> Python |
| `KnowledgeParseResultMQ` | `tolink.rag.parse_result` | Python -> Java |
| `CacheCompensationMQ` | `tolink.cache.evict` | CDC/补偿 -> Java |

## 约定

- 新增消息必须实现 `AbstractMQ`。
- 消息体字段变更必须同步 `docs/reference/mq_contracts.md`。
- Kafka/RabbitMQ 差异应封装在组件适配层，不泄漏到业务 Service。
- `parse_task` 和 `parse_result` 均使用扁平 JSON；任务通过 `document_parse_file_id`、结果通过 `document_parsed_log_id` 与共享数据库记录关联。
- Python 负责解析终态持久化，Java 结果消费者只进行契约校验和 SSE 事件转发。
