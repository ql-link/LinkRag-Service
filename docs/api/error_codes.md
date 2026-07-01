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

- `INVALID_MODEL_CAPABILITY(10011/400)`：模型能力标识无效（合法取值以 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES` 为准：`CHAT` / `EMBEDDING` / `SPARSE_EMBEDDING` / `VISION` / `RERANK` / `ASR`），用于用户侧厂商/配置接口的能力参数校验。
- `MODEL_DISABLED(10012/400)`：选已关停（`is_active=false`）的模型作为某能力生效时拒绝。
- `PRESET_READONLY(10013/403)`：历史保留错误码；系统预设已改由管理端维护在 `llm_system_preset`，普通用户不再通过 `llm_user_config` 操作预设镜像行。
- `MODEL_CONFIG_INCOMPLETE(10014/400)`：模型能力缺少协议或入口，无法保存或展开。触发点：新增模型能力 (`addModelCapability`) 时 `apiBaseUrl` 为空；用户 `setup-provider` 展开时命中协议/入口缺失的历史模型能力（整请求阻断，不静默跳过，避免由执行端猜测）；`createPreset` 命中协议/入口缺失的模型能力。
- `INVALID_PROTOCOL(10015/400)`：协议不在支持范围内。合法取值以 `LLMProtocolServiceImpl.SUPPORTED_PROTOCOLS` 为准（`openai` / `anthropic` / `google` / `jina` / `dashscope`，小写敏感，`OPENAI` 等大写视为非法）。触发点：新增模型能力录入非法 `protocol`。
- `SYSTEM_PROVIDER_READONLY(10016/400)`：系统服务厂商不支持用户自配或启停。当前用于拒绝普通用户通过 `setup-provider` 配置 `provider_type=linkrag`，以及通过 `/api/v1/llm/configs/toggle-model` 启停 LinkRag 只读配置。
- `MODEL_SYNC_SOURCE_UNSUPPORTED(10017/400)`：外部模型目录同步来源不支持，或当前来源未收录该厂商。当前管理端手动刷新只支持 `MODELS_DEV`。
- `MODEL_SYNC_CANDIDATE_NOT_FOUND(10018/404)`：外部模型候选项不存在。用于候选发布和审核状态更新。
- `PROVIDER_HAS_NO_ACTIVE_MODEL(10019/400)`：启用系统厂商前至少需要有一条已上架模型能力。触发点：创建厂商时直接传 `isActive=true`、更新厂商为启用、调用启用/禁用接口启用厂商，但 `llm_provider_model` 中该厂商没有 `is_active=true` 的模型能力。
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
