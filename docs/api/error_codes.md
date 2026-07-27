# Error Codes

错误码事实来源：`link-model/src/main/java/com/qingluo/link/model/enums/ErrorCode.java`。

异常处理事实来源：

- `link-core/src/main/java/com/qingluo/link/core/exception`
- `link-core/src/main/java/com/qingluo/link/core/handler/GlobalExceptionHandler.java`

## 约定

- 新增业务错误优先扩展 `ErrorCode`。
- Controller 不直接拼装异常响应，交给全局异常处理。
- 对外错误语义变化需同步 `docs/api/api_contracts.md`。

## HTTP 协议错误

- 请求方法与已注册路由不匹配时，`GlobalExceptionHandler` 将 `HttpRequestMethodNotSupportedException` 映射为 HTTP 405，响应体为 `Result.error(405, "请求方法不支持")`。例如已删除的 `PATCH /api/v1/admin/document-file-config` 会返回该结果，不再落入通用 500。

## LLM 配置错误码（10001-10999）

统一 `configId` 链路使用以下错误码。精确配置校验的优先级固定为“不存在 → 已停用 → 越权 → 能力不匹配”，三端不得根据调用入口改变顺序：

- `LLM_CONFIG_NOT_FOUND(10020/404)`：`configId` 在统一配置表中不存在。
- `LLM_CONFIG_INACTIVE(10021/409)`：配置存在但已停用。
- `LLM_CONFIG_FORBIDDEN(10022/403)`：USER 配置不属于当前用户；SYSTEM 配置对所有用户可见。
- `LLM_CONFIG_CAPABILITY_MISMATCH(10023/400)`：配置能力与调用点要求不一致。
- `LLM_DEFAULT_NOT_CONFIGURED(10024/409)`：USER 默认不存在且对应 SYSTEM 默认也未配置。
- `LLM_DEFAULT_UPDATE_FAILED(10025/500)`：默认关系在事务内更新失败。
- `LLM_DEFAULT_MUTATION_CONFLICT(10030/400)`：同一请求不能同时设置默认和清除默认。
- `LLM_CONFIG_IN_USE(10026/409)`：配置仍被 `dataset_parse_config` 的任一模型字段引用，禁止删除。
- `LLM_DEFAULT_REPLACEMENT_REQUIRED(10027/409)`：停用或删除当前 SYSTEM 默认前未指定同能力替代项。
- `INVALID_DATASET_MODEL_BINDING(10028/400)`：数据集模型绑定或相关配置参数不合法；响应 `data.field` 指向失败字段。
- `DATASET_MODEL_BINDING_REQUIRED(10029/409)`：启用增强或重排能力时缺少对应 CHAT / VISION / RERANK 配置。

`10001-10019` 属于旧厂商目录与旧双表配置接口的兼容错误码；新统一配置接口不得继续以 `source`、系统预设或用户配置表为身份分支。

- `INVALID_MODEL_CAPABILITY(10011/400)`：模型能力标识无效（合法取值以 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES` 为准：`CHAT` / `EMBEDDING` / `SPARSE_EMBEDDING` / `VISION` / `RERANK` / `ASR`），用于用户侧厂商/配置接口的能力参数校验。
- `MODEL_DISABLED(10012/400)`：选已关停（`is_active=false`）的模型作为某能力生效时拒绝。
- `PRESET_READONLY(10013/403)`：历史保留错误码；统一配置接口不再使用。
- `MODEL_CONFIG_INCOMPLETE(10014/400)`：模型能力缺少协议或入口，无法保存或展开。触发点：新增模型能力 (`addModelCapability`) 时 `apiBaseUrl` 为空；用户 `setup-provider` 展开时命中协议/入口缺失的历史模型能力（整请求阻断，不静默跳过，避免由执行端猜测）；`createPreset` 命中协议/入口缺失的模型能力。
- `INVALID_PROTOCOL(10015/400)`：协议不在支持范围内。合法取值以 `LLMProtocolServiceImpl.SUPPORTED_PROTOCOLS` 为准（`openai` / `anthropic` / `google` / `jina` / `dashscope` / `bge_m3` / `doubao_vision`，小写敏感，`OPENAI` 等大写视为非法）。触发点：新增模型能力录入非法 `protocol`。
- `SYSTEM_PROVIDER_READONLY(10016/400)`：系统服务厂商不支持用户通过 `setup-provider` 生成 USER 配置；平台 SYSTEM 配置由管理端统一配置接口维护。
- `MODEL_SYNC_SOURCE_UNSUPPORTED(10017/400)`：外部模型目录同步来源不支持，或当前来源未收录该厂商。当前管理端手动刷新只支持 `MODELS_DEV`。
- `MODEL_SYNC_CANDIDATE_NOT_FOUND(10018/404)`：外部模型候选项不存在。用于候选发布和审核状态更新。
- `PROVIDER_HAS_NO_ACTIVE_MODEL(10019/400)`：启用系统厂商前至少需要有一条已上架模型能力。触发点：创建厂商时直接传 `isActive=true`、更新厂商为启用、调用启用/禁用接口启用厂商，但 `llm_provider_model` 中该厂商没有 `is_active=true` 的模型能力。
- 其余 `MODEL_NOT_SUPPORTED(10008)`（模型不支持该能力 / 目录无该模型能力）、`DUPLICATE_USER_CONFIG(10009)`、`NO_DEFAULT_CONFIG(10006)` 等以 `ErrorCode.java` 为准。
- `GlobalExceptionHandler` 新增对 `MissingServletRequestParameterException` 的处理：缺少必填查询参数统一返回 400 `缺少必填参数: <name>`。

## 用户与认证错误码（20001-20999）

- `INVALID_USER_STATISTICS_RANGE(20008/400)`：管理端用户统计看板的 `days` 仅支持 `7`、`30`、`90`，缺省为 `30`。

## 上传配置与缓存错误码

- `DOCUMENT_FILE_CONFIG_INVALID(10010/400)`：管理员提交的上传大小非法、超过部署硬上限、后缀列表为空，或包含部署允许全集之外的后缀。
- `CACHE_DELETE_FAILED(50002/500)`：CDC/MQ 补偿删除在重试预算内仍失败。业务写入提交后的首次删除失败不会把这个错误返回给原写请求。
- `DOCUMENT_FILE_CONFIG_UPDATE_FAILED(50003/503)`：管理员上传配置写 Redis 失败；本实例不会提前更新最后有效快照。

## Markdown 图片资源包错误码

- `MARKDOWN_LOCAL_ASSET_REQUIRES_CONTEXT(30010/400)`：Markdown 含本地图片但请求未声明匹配模式，或携带图片却没有资源包上下文。
- `LOCAL_ABSOLUTE_PATH_REJECTED(30011/400)`：引用或目录清单包含 `file:`、Unix/UNC/Windows 绝对路径。
- `ASSET_FILENAME_COLLISION(30012/400)`、`ASSET_PATH_COLLISION(30013/400)`：一级文件名或完整相对路径规范化后冲突。
- `IMAGE_CONTENT_MISMATCH(30014/400)`：扩展名、声明 MIME 与图片魔数不一致。
- `ASSET_FILE_SIZE_LIMIT_EXCEEDED(30016/400)`、`ASSET_COUNT_LIMIT_EXCEEDED(30017/400)`、`ASSET_BUNDLE_SIZE_LIMIT_EXCEEDED(30018/400)`、`ASSET_PATH_LENGTH_EXCEEDED(30019/400)`：资源包硬上限失败；不会创建新文件记录或写 OSS。
- `ASSET_MISSING(30020/409)`：资源包存在缺失、歧义或不支持图片，手动解析未显式确认忽略；响应 `data` 含 `errorKind` 与 `assetSummary`。
- `ASSET_PATH_OUTSIDE_ROOT(30021/400)`：完整路径引用越出上传虚拟树根目录。
- `ASSET_MANIFEST_UNAVAILABLE(50004/503)`：v1 manifest 缺失、损坏、下载失败或与文件归属不一致；解析失败关闭，不按无缺图继续。

## 召回错误码（recall）

> **变更（LINK-122）**：旧召回网关链路（Java 中转代理 `/api/v1/recall/stream`）已废弃移除，其专用错误码
> `RECALL_INVALID_REQUEST(30001)`、`RECALL_RATE_LIMITED(30003)`、`RECALL_INTERNAL_AUTH_FAILED(30004)`、
> `RECALL_ALL_SOURCES_FAILED(30005)`、`RECALL_TIMEOUT(30006)`、`RECALL_UPSTREAM_ERROR(30007)` 与英文串码枚举
> `RecallSseError` 一并删除（这些码仅由该链路的建流前 HTTP / 建流后 SSE 表达使用）。建流后 SSE 错误码现由 Python 直连链路负责。

召回 session 签发链路（`POST /api/v1/recall/sessions`，前端直连 Python，LINK-104）只保留归属校验相关 HTTP 错误：

- `RECALL_SCOPE_FORBIDDEN(30002/403)`：`datasetIds` 含非本人/已软删的数据集。
- 用户禁用复用 `AUTH_DISABLED(20003/403)`；`datasetIds` 为空或缺省由 DTO 校验返回 400。

详见 `docs/api/api_contracts.md` 的 Recall 章节。
