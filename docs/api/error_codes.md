# Error Codes

错误码事实来源：`link-model/src/main/java/com/qingluo/link/model/enums/ErrorCode.java`。

异常处理事实来源：

- `link-core/src/main/java/com/qingluo/link/core/exception`
- `link-core/src/main/java/com/qingluo/link/core/handler/GlobalExceptionHandler.java`

## 约定

- 新增业务错误优先扩展 `ErrorCode`。
- Controller 不直接拼装异常响应，交给全局异常处理。
- 对外错误语义变化需同步 `docs/api/api_contracts.md`。

## LLM 配置错误码（10001-10999）

- `INVALID_MODEL_CAPABILITY(10011/400)`：模型能力标识无效（合法取值以 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES` 为准：`CHAT` / `EMBEDDING` / `OCR` / `VISION` / `REASONING` / `CODE` / `TOOL_CALLING` / `RERANK`），用于用户侧厂商/配置接口的能力参数校验。
- `MODEL_DISABLED(10012/400)`：选已关停（`is_active=false`）的模型作为某能力生效时拒绝。
- `PRESET_READONLY(10013/403)`：系统预设只读，删除预设或修改其内容时拒绝。
- `MODEL_CONFIG_INCOMPLETE(10014/400)`：模型能力缺少协议或入口，无法保存或展开。触发点：新增模型能力 (`addModelCapability`) 时 `apiBaseUrl` 为空；用户 `setup-provider` 展开时命中协议/入口缺失的历史模型能力（整请求阻断，不静默跳过，避免由执行端猜测）；`createPreset` 命中协议/入口缺失的模型能力。
- `INVALID_PROTOCOL(10015/400)`：协议不在支持范围内。合法取值以 `LLMProtocolServiceImpl.SUPPORTED_PROTOCOLS` 为准（`openai` / `anthropic` / `google` / `jina` / `dashscope`，小写敏感，`OPENAI` 等大写视为非法）。触发点：新增模型能力录入非法 `protocol`。
- 其余 `MODEL_NOT_SUPPORTED(10008)`（模型不支持该能力 / 目录无该模型能力）、`DUPLICATE_USER_CONFIG(10009)`、`NO_DEFAULT_CONFIG(10006)` 等以 `ErrorCode.java` 为准。
- `GlobalExceptionHandler` 新增对 `MissingServletRequestParameterException` 的处理：缺少必填查询参数统一返回 400 `缺少必填参数: <name>`。

## 召回错误码（recall-gateway）

召回网关采用**双通道**错误表达：

- **建流前（HTTP 错误）**：沿用数字 `ErrorCode`，召回段 `30001-39999`，经 `GlobalExceptionHandler` 返回 `Result`：
  `RECALL_INVALID_REQUEST(30001/400)`、`RECALL_SCOPE_FORBIDDEN(30002/403)`、`RECALL_RATE_LIMITED(30003/429)`、
  `RECALL_INTERNAL_AUTH_FAILED(30004)`、`RECALL_ALL_SOURCES_FAILED(30005)`、`RECALL_TIMEOUT(30006)`、`RECALL_UPSTREAM_ERROR(30007)`。
  另：用户禁用复用 `AUTH_DISABLED(20003/403)`；请求体不可读（未知字段/类型错误）由 `GlobalExceptionHandler.handleNotReadable` 返回 400。
- **建流后（SSE `error` 事件）**：用英文串码枚举 `RecallSseError`（`UNAUTHORIZED` / `RECALL_SCOPE_FORBIDDEN` / `RECALL_INVALID_REQUEST` /
  `RECALL_INTERNAL_AUTH_FAILED` / `RECALL_ALL_SOURCES_FAILED` / `RECALL_TIMEOUT` / `RECALL_UPSTREAM_ERROR`），
  `data: {"code","message"}`，不含内部堆栈。Python 已知错误码透传，未知/非 2xx 兜底 `RECALL_UPSTREAM_ERROR`。

详见 `docs/api/api_contracts.md` 的 Recall 章节。
