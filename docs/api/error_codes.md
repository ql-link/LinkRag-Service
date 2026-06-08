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
- 其余 `MODEL_NOT_SUPPORTED(10008)`（模型不支持该能力 / 目录无该模型能力）、`DUPLICATE_USER_CONFIG(10009)`、`NO_DEFAULT_CONFIG(10006)` 等以 `ErrorCode.java` 为准。
- `GlobalExceptionHandler` 新增对 `MissingServletRequestParameterException` 的处理：缺少必填查询参数统一返回 400 `缺少必填参数: <name>`。

## 召回错误码（recall）

> **变更（LINK-122）**：旧召回网关链路（Java 中转代理 `/api/v1/recall/stream`）已废弃移除，其专用错误码
> `RECALL_INVALID_REQUEST(30001)`、`RECALL_RATE_LIMITED(30003)`、`RECALL_INTERNAL_AUTH_FAILED(30004)`、
> `RECALL_ALL_SOURCES_FAILED(30005)`、`RECALL_TIMEOUT(30006)`、`RECALL_UPSTREAM_ERROR(30007)` 与英文串码枚举
> `RecallSseError` 一并删除（这些码仅由该链路的建流前 HTTP / 建流后 SSE 表达使用）。建流后 SSE 错误码现由 Python 直连链路负责。

召回 session 签发链路（`POST /api/v1/recall/sessions`，前端直连 Python，LINK-104）只保留归属校验相关 HTTP 错误：

- `RECALL_SCOPE_FORBIDDEN(30002/403)`：`datasetIds` 含非本人/已软删的数据集。
- 用户禁用复用 `AUTH_DISABLED(20003/403)`；`datasetIds` 为空或缺省由 DTO 校验返回 400。

详见 `docs/api/api_contracts.md` 的 Recall 章节。
