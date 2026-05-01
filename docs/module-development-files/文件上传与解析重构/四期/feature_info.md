# 功能信息卡

## 1. 基础信息

- 模块名称：文件上传与解析重构
- 当前期次：四期
- 业务域：storage
- 当前状态：范围待确认
- 复杂度等级：L3
- 当前分支：refactor/update-file-upload-parse

## 2. 功能摘要

- 背景：三期将聚焦“Python 终态结果通过 `parse_result` MQ 回传 Java”，为了避免范围膨胀，其余候选增强项统一顺延到四期。
- 四期定位：
  - 承接三期未纳入的增强型需求。
  - 主要处理一致性增强、能力补全和长期治理问题。

## 3. 四期候选目标

| 序号 | 目标 | 描述 | 优先级 |
| :--- | :--- | :--- | :--- |
| 1 | 前端直连 Python WebSocket 推送 | 前端浏览器通过 WebSocket 直连 Python 解析服务，获取实时进度与终态结果，绕过 Java 中间透传层 | 最高 |
| 2 | 原文件删除语义升级 | 设计原文件隐藏、解析日志保留、解析产物清理规则 | 中 |
| 3 | 解析产物版本管理 | 支持同一原文件保留多次成功解析版本 | 中 |
| 4 | MQ 投递可靠性增强 | 评估 Kafka ack 确认、Outbox / 本地消息表等增强方案 | 中 |
| 5 | 悬空解析任务失败化治理 | 针对 `latest_parse_task_id` 已存在但长时间无 `document_parse_log` 的情况设计超时判定、巡检或补偿策略 | 中 |
| 6 | 解析日志查询与归档治理 | 补齐历史查询、索引与清理治理方案 | 中 |
| 7 | 解析后下游知识处理衔接 | 接入向量化、检索、问答等后续能力 | 低 |

### 3.1 目标 1 详细说明：前端直连 Python WebSocket

**背景与动机：**

当前进度与终态事件的推送链路为：

```text
Python --HTTP 回调--> Java --SSE--> Browser（进度）
Python --Kafka MQ--> Java --SSE--> Browser（终态）
```

Java 在这条链路中是纯透传节点，引入以下问题：
1. **延迟叠加** — Python → Java（HTTP/MQ）→ Browser（SSE），多一跳
2. **多实例问题** — SSE 连接保存在 Java JVM 内存，回调打到无该连接的实例时前端收不到推送
3. **可靠性** — Java 重启/扩缩容会断连，前端需要重连兜底

**目标架构：**

```text
Python --WebSocket--> Browser（进度 + 终态，直连）
Java 职责退化为：上传、元数据管理、解析任务提交、结果查询兜底
```

**认证方案：短期 Token 交换**

```text
前端 --satoken--> Java：申请 WebSocket 连接 Token
Java 生成有时效的短期 Token（写入 Redis，TTL 5 分钟）
Java 返回 Token + Python WebSocket 地址给前端
前端 --Token--> Python：建立 WebSocket 连接
Python --Token--> Java 内部接口：验证 Token 有效性
连接建立，Python 直接推送进度/终态事件
```

- 短期 Token 与用户登录态（sa-token）解耦，不暴露用户凭据给 Python
- Token 一次性使用，连接建立后即失效
- Python 通过调用 Java 内部验证接口确认 Token，无需接入 sa-token 协议

**Java 端影响：**

| 变更项 | 说明 |
| :--- | :--- |
| 新增 Token 申请接口 | `POST /api/v1/files/ws-token`，返回 `{token, wsUrl}` |
| 新增 Token 验证内部接口 | `POST /api/v1/internal/ws-tokens/{token}/verify`，Python 调用 |
| 废弃 SSE 推送 | `KnowledgeParseSseService` 标记废弃，后续移除 |
| 废弃 HTTP 进度回调 | `InternalKnowledgeFileController.parseEvent(...)` 标记废弃 |
| 保留 parse_result MQ | 暂时保留作为 Java 侧日志记录和兜底查询的数据来源 |
| 保留结果查询接口 | `GET /api/v1/datasets/{datasetId}/files/parse-results` 作为兜底 |

**前端影响：**

| 变更项 | 说明 |
| :--- | :--- |
| 新增 WebSocket 连接逻辑 | 向 Java 申请 Token，再用 Token 连接 Python |
| 废弃 SSE 订阅 | 不再连接 `GET /api/v1/datasets/{datasetId}/files/parse-events` |
| 保留结果查询兜底 | WebSocket 断线时仍可通过查库接口获取状态 |

**Python 端影响：**

| 变更项 | 说明 |
| :--- | :--- |
| 新增 WebSocket 服务 | 暴露 WebSocket 端点，管理连接生命周期 |
| 新增 Token 验证逻辑 | 收到连接请求时调 Java 内部接口验证 Token |
| 废弃 HTTP 进度回调 | 不再调用 `POST /api/v1/internal/parse-tasks/{taskId}/events` |
| 保留 parse_result MQ | 写库终态后仍发 MQ，供 Java 记录日志和兜底查询 |

## 4. 文档清单

- `feature_info.md`
- 后续待补：`requirement.md`
- 后续待补：`technical_design.md`
- 后续待补：`implementation_report.md`
- 后续待补：`testing_delivery.md`

## 5. 关联期次

- 一期：上传链路与解析聚合记录初始化
- 二期：解析提交、`parse_task` MQ 与 Python 回调
- 三期：终态 `parse_result` MQ 回传
- 四期：前端直连 Python WebSocket 推送 + 多实例、一致性与治理增强

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `../三期/feature_info.md`
3. `../三期/requirement.md`
4. `../二期/technical_design.md`
5. `AGENTS.md`
6. `project_info.md`
7. `docs/architecture/middleware_contract.md`
