# Error Codes

错误码事实来源：`link-model/src/main/java/com/qingluo/link/model/enums/ErrorCode.java`。

异常处理事实来源：

- `link-core/src/main/java/com/qingluo/link/core/exception`
- `link-core/src/main/java/com/qingluo/link/core/handler/GlobalExceptionHandler.java`

## 约定

- 新增业务错误优先扩展 `ErrorCode`。
- Controller 不直接拼装异常响应，交给全局异常处理。
- 对外错误语义变化需同步 `docs/reference/api_contracts.md`。

## 召回错误码（recall-gateway）

召回网关采用**双通道**错误表达：

- **建流前（HTTP 错误）**：沿用数字 `ErrorCode`，召回段 `30001-39999`，经 `GlobalExceptionHandler` 返回 `Result`：
  `RECALL_INVALID_REQUEST(30001/400)`、`RECALL_SCOPE_FORBIDDEN(30002/403)`、`RECALL_RATE_LIMITED(30003/429)`、
  `RECALL_INTERNAL_AUTH_FAILED(30004)`、`RECALL_ALL_SOURCES_FAILED(30005)`、`RECALL_TIMEOUT(30006)`、`RECALL_UPSTREAM_ERROR(30007)`。
  另：用户禁用复用 `AUTH_DISABLED(20003/403)`；请求体不可读（未知字段/类型错误）由 `GlobalExceptionHandler.handleNotReadable` 返回 400。
- **建流后（SSE `error` 事件）**：用英文串码枚举 `RecallSseError`（`UNAUTHORIZED` / `RECALL_SCOPE_FORBIDDEN` / `RECALL_INVALID_REQUEST` /
  `RECALL_INTERNAL_AUTH_FAILED` / `RECALL_ALL_SOURCES_FAILED` / `RECALL_TIMEOUT` / `RECALL_UPSTREAM_ERROR`），
  `data: {"code","message"}`，不含内部堆栈。Python 已知错误码透传，未知/非 2xx 兜底 `RECALL_UPSTREAM_ERROR`。

详见 `docs/reference/api_contracts.md` 的 Recall 章节。
