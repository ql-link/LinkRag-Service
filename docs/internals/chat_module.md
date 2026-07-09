# 对话模块

本文档描述 Java 端对话会话管理和 Python `chat_turn` 消息落库。

## 模块边界

代码入口：

| 职责 | 入口 |
| --- | --- |
| 会话 CRUD | `ChatServiceImpl` |
| chat_turn 落库 | `ChatTurnPersistenceServiceImpl` |
| MQ 消费 | `ChatTurnKafkaReceiver`、`ChatTurnConsumer` |
| HTTP 入口 | `ChatController` |

核心表：

| 表 | 作用 |
| --- | --- |
| `chat_conversation` | 用户会话、所属数据集、标题、置顶和最后使用模型 |
| `chat_message` | 每轮问答、状态、引用、错误信息 |

## 会话管理

HTTP 接口在 `/api/v1/chat/conversations` 下：

| 接口 | 行为 |
| --- | --- |
| `POST /` | 创建会话，要求 datasetId 属于当前用户 |
| `GET /` | 分页查询会话，置顶优先，其次按更新时间倒序 |
| `GET /{id}/messages` | 分页查询消息，按创建时间正序 |
| `PATCH /{id}` | 更新标题或置顶状态 |
| `DELETE /{id}` | 删除会话及其消息 |

创建会话默认标题为 `新对话`。用户手动改名后，后续 Python 上报标题不会覆盖用户标题。

## chat_turn 落库

Python 通过 MQ 上报一轮对话状态。Java 消费后按 `(conversation_id, turn_id)` 定位同一轮消息：

1. 校验 conversation 存在且属于 payload.userId。
2. 若消息不存在，插入新行，可插入 `GENERATING` 起点，也可接受迟到终态先到。
3. 若已有终态，重复消息直接忽略。
4. 若已有 `GENERATING`，只有终态消息可以推进状态。
5. 推进终态时补齐 answer、references、错误信息、模型和配置快照。
6. 更新会话的 `last_config_id`、`last_model_name`、`updated_at`。

状态不回退是本模块的核心约束：终态不会被迟到的 `GENERATING` 覆盖。

## 与用量模块的边界

自 LINK-191 起，`chat_turn` 只负责持久化对话内容，不再写 `llm_usage_log`。

对话 generate 的 token 用量由统一 `tolink.rag.usage_report` 消息承接，使用 `stage=chat`、`operation=generate`。排查账本问题时应看用量模块，不应在 chat_turn 消费链路补写用量。

## 修改注意事项

- `turn_id` 来自客户端或 Python 链路，落库必须结合 conversationId 防止跨会话覆盖。
- Python 只透传 userId 和 conversationId；Java 消费侧必须做归属校验。
- 新增消息状态时需同步 `ChatTurnStatus`、MQ 契约和状态推进逻辑。
- 会话 API 变更需同步 `docs/api/api_contracts.md`。
