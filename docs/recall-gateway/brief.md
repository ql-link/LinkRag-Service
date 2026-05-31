# Java Recall Gateway SSE Brief

## 1. 需求摘要

- **做什么**：在 Java 管理端新增用户态召回 SSE 接口，由 Java 负责 Sa-Token 登录校验、用户状态校验、dataset 权限校验，并调用 Python internal recall stream 获取召回结果后转发给前端。
- **为什么做**：Python RAG 端只提供内部召回 runtime，不承担用户登录态和业务权限判断。前端必须先经过 Java 这一用户态安全边界，避免直接调用 Python 造成越权召回。
- **本次必须交付**：Java 对前端的流式接口 `POST /api/v1/recall/stream`，以及 Java 到 Python 的内部调用适配。
- **本次不交付**：前端直连 Python SSE、Java 普通一次性 JSON 召回接口、rerank / context / answer 生成链路。

## 2. 上下游边界

### 2.1 Java 对前端

Java 是用户态 API Gateway：

- 校验 Sa-Token 登录态。
- 获取当前登录用户 ID。
- 校验用户状态是否允许召回。
- 校验请求中的 `datasetIds` 是否归属当前用户或当前用户有访问权限。
- 建立对前端的 SSE 响应。
- 调用 Python internal recall stream，并把 Python 结果转换或透传为前端 SSE 事件。

### 2.2 Java 对 Python

Python 是内部 recall runtime：

- Java 调用 `POST /api/v1/internal/recall/stream`。
- Java 使用 HS256 签发内部 JWT。
- Java 请求体使用 Python snake_case 字段：`query`、`user_id`、`dataset_ids`。
- Python 成功时返回 SSE `recall_done`，失败时返回 SSE `error` 或建流前 HTTP 错误。

Java 不把前端 Sa-Token 透传给 Python。Python 不直接接受前端请求。

## 3. Java 对前端 API 契约

### 3.1 流式召回接口

```http
POST /api/v1/recall/stream
satoken: <java-login-token>
Accept: text/event-stream
Content-Type: application/json
```

请求体：

```json
{
  "query": "用户问题",
  "datasetIds": [1, 2]
}
```

字段约束：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `query` | string | 是 | 用户原始问题，不能为空或纯空白 |
| `datasetIds` | list<long> | 是 | Java 已校验的数据集范围；空列表表示当前用户被授权全库召回 |

首版不接收 `docIds`、`topK`、`sources`、`strict`、`includeContent`。如果前端传入这些字段，Java 应返回参数错误，避免前端误以为这些策略会生效。

### 3.2 Java 对前端 SSE 事件

Java 对前端的首版事件保持最小化：

```text
event: recall_done
data: {"hits":[{"chunkId":"1001","docId":10,"datasetId":1}]}
```

```text
event: error
data: {"code":"RECALL_ALL_SOURCES_FAILED","message":"all retrievers failed"}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `hits` | 融合后的最终召回候选，保持 Python 返回顺序 |
| `chunkId` | chunk 唯一标识 |
| `docId` | chunk 所属文档 ID |
| `datasetId` | chunk 所属数据集 ID |
| `code` | 错误码 |
| `message` | 错误说明，不返回内部堆栈 |

本需求冻结为：Java 对前端统一使用 camelCase；首版不返回 chunk 正文，也不在 Java 侧补充正文回查。

## 4. Java 调 Python 契约

### 4.1 请求

```http
POST /api/v1/internal/recall/stream
Authorization: Bearer <internal-jwt>
Accept: text/event-stream
Content-Type: application/json
X-Request-Id: <request-id>
```

请求体：

```json
{
  "query": "用户问题",
  "user_id": 123,
  "dataset_ids": [1, 2]
}
```

Java 映射规则：

| Java 前端字段 / 上下文 | Python 字段 |
| --- | --- |
| `query` | `query` |
| 当前登录用户 ID | `user_id` |
| `datasetIds` | `dataset_ids` |

### 4.2 内部 JWT

Java 使用 HS256 签发内部 JWT。建议 claims：

```json
{
  "iss": "tolink-java",
  "aud": "tolink-rag",
  "sub": "123",
  "scope": "recall:execute",
  "dataset_ids": [1, 2],
  "jti": "request-id",
  "exp": 1710000300
}
```

签发要求：

- `sub` 必须等于当前登录用户 ID。
- `dataset_ids` 必须等于 Java 已校验通过的授权范围。
- 空 `dataset_ids` 只允许在 Java 明确判断当前用户可全库召回时签发。
- `scope` 固定为 `recall:execute`。
- `jti` 使用本次请求的 `request_id`。
- `exp` 使用短有效期，具体秒数在技术设计中配置化。

### 4.3 Python 响应处理

Python 成功事件：

```text
event: recall_done
data: {"hits":[...],"failed_sources":[]}
```

Java 处理：

- 解析 Python `hits`，并只向前端输出 `chunkId`、`docId`、`datasetId`。
- Python 返回的 `fused_score`、`scores`、`failed_sources` 不进入前端 SSE 契约；Java 可按需记录日志或指标。
- 向前端发送 `event: recall_done`。
- 发送后关闭前端 SSE。

Python 失败事件：

```text
event: error
data: {"code":"RECALL_ALL_SOURCES_FAILED","message":"all retrievers failed"}
```

Java 处理：

- 不暴露 Python 内部堆栈。
- 可按原错误码透传，或映射为 Java 统一错误码。
- 向前端发送 `event: error`。
- 发送后关闭前端 SSE。

Python 建流前 HTTP 错误：

- `401/403/422/500/502/504` 等非 2xx 响应由 Java 映射为前端 `error` SSE 事件。
- Java 需要记录 Python HTTP status、错误码和 `request_id`，便于排查。

## 5. 断连、超时与资源释放

- 前端断开 Java SSE 时，Java 必须关闭到 Python 的内部 stream。
- Python stream 关闭后，Java 必须关闭前端 SSE。
- Java 调 Python 应设置连接超时、读取超时和整体执行超时。
- Java 和部署网关必须关闭 SSE 响应缓冲，避免前端迟迟收不到事件。
- Java 应设置 `Content-Type: text/event-stream`、`Cache-Control: no-cache`。
- Java 应按用户或 IP 做对外召回限流，避免长连接耗尽线程或连接池。

## 6. 错误映射

| 场景 | Java 对前端行为 | 建议错误码 |
| --- | --- | --- |
| Sa-Token 缺失或无效 | 建流前返回 `401`，或 SSE `error` 后关闭 | `UNAUTHORIZED` |
| 用户无权访问 dataset | 建流前返回 `403`，或 SSE `error` 后关闭 | `RECALL_SCOPE_FORBIDDEN` |
| `query` 为空或参数类型错误 | 建流前返回 `400/422` | `RECALL_INVALID_REQUEST` |
| Python 内部 JWT 被拒绝 | SSE `error` 后关闭 | `RECALL_INTERNAL_AUTH_FAILED` |
| Python 返回全路失败 | SSE `error` 后关闭 | `RECALL_ALL_SOURCES_FAILED` |
| Python 超时 | SSE `error` 后关闭 | `RECALL_TIMEOUT` |
| 前端主动断连 | 不作为业务错误 | 记录审计日志并取消 Python stream |

如果 Java 已经向前端发送了 SSE 响应头，后续错误必须用 SSE `error` 表达；如果还没有建流，可以使用普通 HTTP 错误响应。

## 7. 验收要点

- 未登录或 Sa-Token 无效时，Java 不调用 Python。
- `datasetIds` 超出当前用户权限时，Java 不调用 Python。
- Java 调 Python 时，body.user_id 与 JWT `sub` 一致。
- Java 调 Python 时，body.dataset_ids 与 JWT `dataset_ids` 一致。
- `datasetIds=[]` 只有在 Java 明确授权全库召回时才允许。
- Python 返回 `recall_done` 时，Java 向前端输出 `recall_done`，并包含最小候选 `hits`。
- Python 返回 `error` 或建流前非 2xx 时，Java 向前端输出或返回对应错误，不暴露内部堆栈。
- 前端断连时，Java 关闭 Python stream。
- 前端传入 `docIds/topK/sources/strict/includeContent` 时，Java 返回参数错误。

## 8. 配置建议

| 配置 | 默认建议 | 说明 |
| --- | --- | --- |
| `RECALL_INTERNAL_JWT_SECRET` | 本地联调示例：`9780df1524906ac133898a8cc74280c512f0334d32d795786c021059ec09b5da` | HS256 JWT 共享密钥；Java 签发端和 Python 验签端必须一致。该值仅用于本地联调，生产环境必须通过环境变量或密钥管理系统替换 |
| `RAG_PYTHON_BASE_URL` | 按部署环境配置 | Python RAG 服务地址，用于拼接 `/api/v1/internal/recall/stream` |
| `RECALL_STREAM_TIMEOUT_MS` | `60000` | Java 等待 Python stream 的整体超时时间 |

## 9. 风险与实现注意

| 风险 / 问题 | 影响 | 建议 |
| --- | --- | --- |
| Java SSE 中转占用连接资源 | 高并发长连接下 Java 压力增加 | 首版接受；后续用 `LINK-40` 的短期 token 直连 Python SSE 演进 |
| 空 `datasetIds` 误当全库授权 | 可能越权召回 | Java 必须显式判断全库授权，不允许默认空列表 |
| Java/Python 字段风格不同 | 前端和 Python 契约混淆 | Java 对前端用 camelCase，对 Python 用 snake_case |
| 建流前后错误表达不同 | 前端处理复杂 | 明确：建流前可 HTTP 错误，建流后统一 SSE `error` |
| Python 降级成功对前端不可见 | 前端无法判断是否部分召回路失败 | 首版前端不展示召回路健康度；Java 可记录 `failed_sources` 指标，后续如需要再扩展前端事件 |

## 10. 关联

- Python internal recall stream brief: `.specs/recall-http-api/brief.md`
- Related issue: `LINK-35`
- Follow-up optimization: `LINK-40`
