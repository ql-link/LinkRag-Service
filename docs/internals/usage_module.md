# 用量模块

本文档描述统一 token 用量上报、落库和查询口径。

## 模块边界

代码入口：

| 职责 | 入口 |
| --- | --- |
| usage_report 落库 | `UsageReportPersistenceServiceImpl` |
| MQ 消费 | `UsageReportKafkaReceiver`、`UsageReportConsumer` |
| 查询服务 | `UsageQueryServiceImpl` |
| HTTP 入口 | `UsageController` |

核心表：

| 表 | 作用 |
| --- | --- |
| `llm_usage_log` | 每次模型调用的旁路账本 |

## 上报语义

Python 或其他执行端通过 `tolink.rag.usage_report` 上报模型调用用量。Java 每条消息落 `llm_usage_log` 一行。

账本字段包括：

| 字段 | 说明 |
| --- | --- |
| `user_id` | 归属用户 |
| `config_id` | 用户配置或系统配置，可为空 |
| `provider_type` | 厂商类型 |
| `model_name` | 模型名 |
| `stage` | 调用阶段，例如 `parse`、`recall`、`chat` |
| `operation` | 阶段内操作，例如 `generate`、`embed`、`rerank` |
| `prompt_tokens` / `completion_tokens` / `total_tokens` | token 统计 |
| `latency_ms` | 调用耗时，可为空 |
| `status` | 缺省为 `success` |

`task_id` 当前只用于日志排障，不落独立列。

## 幂等与一致性

用量上报是旁路、最终一致账本。MQ 按 at-least-once 处理，偶发重复可接受，当前没有强去重。

向量类调用的 `completion_tokens = 0` 是正常值。`config_id = NULL` 表示系统配置调用或执行端无法归属到用户配置。

## 查询口径

HTTP 接口在 `/api/v1/llm/usage` 下：

| 接口 | 默认口径 |
| --- | --- |
| `/summary` | 默认 `stage=chat`，传 `stage=all` 查全链路 |
| `/daily` | 默认 `stage=chat`，传 `stage=all` 查全链路 |
| `/logs` | 默认 `stage=chat`，传 `stage=all` 查全链路 |
| `/by-model` | 全链路，不按 stage 过滤 |
| `/trend` | 全链路，不按 stage 过滤 |

平均延迟只统计成功调用，避免失败和超时拉偏均值。环比趋势使用当前日期范围和紧邻的等长上一周期对比；上一周期为 0 时增长率返回 `null`。

## 与对话模块的边界

对话内容由 `chat_turn` 消息落到 `chat_message`。对话 generate 用量由 `usage_report` 落到账本，二者不互相补写。

## 修改注意事项

- 新增 stage 或 operation 时需同步 MQ 契约、API 文档和前端筛选项。
- 不要在 chat_turn 消费链路中恢复用量入账。
- 如需强幂等，应先明确 message_id 或业务唯一键，并同步 Python 侧 schema。
