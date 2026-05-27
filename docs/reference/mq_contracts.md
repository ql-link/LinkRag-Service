# MQ Contracts

MQ 实现事实来源：

- `link-components/toLink-components-mq`
- `link-service/src/main/java/com/qingluo/link/service/mq`
- `link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/model`

## 消息清单

| 消息模型 | Topic/Queue | 方向 | 说明 |
| --- | --- | --- | --- |
| `KnowledgeParseTaskMQ` | `tolink.rag.parse_task` | Java -> Python | 知识文件解析任务 |
| `KnowledgeParseResultMQ` | `tolink.rag.parse_result` | Python -> Java | 解析终态结果 |
| `CacheCompensationMQ` | `tolink.cache.evict` | 补偿生产者 -> Java | 缓存补偿删除 |

## 契约要求

- Topic/Queue 名称变更是破坏性变更。
- 消息字段新增、删除、重命名、类型变化必须同步本文档和相关消费方。
- Java/Python 双端共享的消息必须保持幂等字段和状态字段语义稳定。
