# API Contracts

本文件是 HTTP API 摘要。实现事实来源为 `link-api/src/main/java/com/qingluo/link/api/controller` 和 `link-model/src/main/java/com/qingluo/link/model/dto`。

## Auth

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | 登录 |
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/logout` | 退出 |

## User / Admin

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/user/profile` | 当前用户资料 |
| PATCH | `/api/v1/user/profile` | 更新当前用户资料 |
| GET | `/api/v1/admin/users` | 管理员用户列表 |
| PATCH | `/api/v1/admin/users/{id}/status` | 启用/禁用用户 |
| PATCH | `/api/v1/admin/users/{id}/role` | 修改用户角色 |
| GET | `/api/v1/admin/document-file-config` | 查询文档文件上传配置 |
| PATCH | `/api/v1/admin/document-file-config` | 修改文档文件上传配置 |
| POST | `/api/v1/admin/providers/{providerId}/models` | 新增厂商模型能力目录项 |
| DELETE | `/api/v1/admin/provider-models/{id}` | 删除模型能力目录项 |
| PATCH | `/api/v1/admin/provider-models/{id}/active` | 上/下架模型能力目录项 |
| GET | `/api/v1/admin/system-presets` | 系统预设列表（平台 Key 脱敏） |
| POST | `/api/v1/admin/system-presets` | 新增系统预设（平台 Key 加密入库） |
| DELETE | `/api/v1/admin/system-presets/{id}` | 删除系统预设 |

## LLM

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/llm/providers` | 可用厂商与模型能力（来源 `llm_provider_model`） |
| GET | `/api/v1/llm/configs` | 用户配置列表（含系统预设行） |
| POST | `/api/v1/llm/configs/setup-provider` | 配置厂商：选厂商+填厂商级 Key，自动展开整厂商模型 |
| PATCH | `/api/v1/llm/configs/toggle-model` | 模型启停（按厂商+模型批量） |
| PUT | `/api/v1/llm/configs/effective` | 按能力选生效模型 |
| GET | `/api/v1/llm/configs/default` | 取某能力生效配置 |
| PATCH | `/api/v1/llm/configs/{id}/default` | 设某能力生效（含切换到/切回系统预设） |
| DELETE | `/api/v1/llm/configs/{id}` | 删除配置（系统预设只读，拒删） |
| GET | `/api/v1/llm/usage/summary` | 用量汇总 |
| GET | `/api/v1/llm/usage/daily` | 日度用量 |
| GET | `/api/v1/llm/usage/logs` | 用量明细 |

> `configs` 相关响应（`UserLLMConfigDTO`）的能力字段为单数 `capability`（合法取值 `CHAT` / `EMBEDDING` / `OCR` / `VISION` / `REASONING` / `CODE` / `TOOL_CALLING` / `RERANK`，事实来源 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES`），曾误用复数 `capabilities`，前端需按 `capability` 取值。
>
> 用户侧 `GET /api/v1/llm/providers`（`ProviderController`）查询启用中的厂商与模型，供用户添加配置前选择，支持按 `capability` 过滤，返回 `ProviderModelDTO`；与管理端 `GET /api/v1/admin/providers`（分页管理视图）区分用途。
>
> 两步配置：`POST /configs/setup-provider`（选厂商 + 填厂商级 Key，按 `llm_provider_model` 展开整厂商「模型×能力」为多条自配并返回列表，重复配置同厂商则更新其 Key）→ `PUT /configs/effective`（按能力选一个启用模型生效，单用户单能力生效唯一）。`PATCH /configs/toggle-model` 独立启停模型（关停后按能力选生效时不展示）。系统预设注册时写入用户配置（`is_system_preset=true`、`is_default=true`），常备只读，仅可经 `PATCH /configs/{id}/default` 按能力切换是否选其生效。`GET /configs` 支持 `capability` / `isActive` 过滤。错误码：删除预设 `10013`、选已关停模型生效 `10012`、模型不支持能力 `10008`、无效能力 `10011`。旧 `POST /configs`、`PATCH /configs/{id}` 已移除（不兼容）。

## Chat

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/chat/conversations` | 创建会话 |
| GET | `/api/v1/chat/conversations` | 会话列表 |
| GET | `/api/v1/chat/conversations/{id}/messages` | 消息列表 |
| PATCH | `/api/v1/chat/conversations/{id}` | 更新会话 |
| POST | `/api/v1/chat/conversations/{id}/messages` | 保存消息 |
| DELETE | `/api/v1/chat/conversations/{id}` | 删除会话 |

## Dataset / Document File

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/datasets` | 创建数据集 |
| GET | `/api/v1/datasets` | 数据集列表 |
| GET | `/api/v1/datasets/{datasetId}` | 数据集详情 |
| PATCH | `/api/v1/datasets/{datasetId}` | 更新数据集 |
| DELETE | `/api/v1/datasets/{datasetId}` | 删除数据集 |
| POST | `/api/v1/datasets/{datasetId}/files` | 上传文档文件（异步：立即返回 `uploadStatus=UPLOADING`） |
| GET | `/api/v1/datasets/{datasetId}/files` | 文件列表（支持按 `uploadStatus` 过滤，前端据此轮询上传终态） |
| GET | `/api/v1/files/recent` | 当前用户全局最近文档列表 |
| GET | `/api/v1/files/{fileId}` | 文件详情 |
| DELETE | `/api/v1/files/{fileId}` | 删除文件 |
| POST | `/api/v1/files/{fileId}/parse` | 提交解析 |
| GET | `/api/v1/datasets/{datasetId}/files/parse-events` | SSE 解析事件 |
| GET | `/api/v1/datasets/{datasetId}/files/parse-results` | 解析结果列表 |

> 文档上传异步化：`POST .../files` 在同步校验（鉴权/数据集归属/格式/大小/文件名/同名）通过后立即返回 `uploadStatus=UPLOADING`；OSS 上传与终态回写（`UPLOAD_SUCCESS`/`UPLOAD_FAILED`）在后台线程池异步完成。同步校验失败仍即时返回 4xx（未登录/无权 401-404、格式/大小/文件名/同名 400）。前端需按 `uploadStatus` 轮询 list/detail 获取终态。同名重试：撞到 `UPLOAD_FAILED` 同名文件会复用原记录重传，撞到 `UPLOADING`/`UPLOAD_SUCCESS` 返回 400。

## OSS / Internal

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/oss-files/{bizType}` | 通用 OSS 上传 |
| GET | `/api/v1/internal/files/{fileId}/content` | Python 端读取私有文件内容 |
| POST | `/api/v1/internal/parse-tasks/{taskId}/events` | Python 端推送解析过程事件 |

统一响应模型为 `Result<T>`，分页模型为 `PageResult<T>`。

解析过程接口只接受 `processing` / `progress`；终态结果通过 `tolink.rag.parse_result` MQ 推送。解析结果查询读取 `document_parse_file.latest_parse_task_id` 所指向的日志状态。

## Recall

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/recall/sessions` | 签发前端直连 Python 召回的短期 session token（普通 JSON） |

> **历史链路已下线（LINK-122）**：Java 曾提供 `POST /api/v1/recall/stream` 中转代理（同步转发 Python 内部端点 `/api/v1/internal/recall/stream`），现已废弃移除。聊天召回统一走「前端直连 Python」：前端凭下方接口签发的 session token 直连 `streamUrl`（即 `<rag-host>/api/v1/recall/stream`，由 Python 提供），Java **不在**召回/生成请求路径上。

### POST /api/v1/recall/sessions（前端直连签发）

> 「前端直连 Python 召回 SSE」链路（LINK-104）：Java 只做 Sa-Token 鉴权 + 用户状态（`status==1`）+ `datasetIds` 归属校验，签发短期 HS256 session token；**不代理/中转 SSE 流内容**，资源滥用由 Python「按用户并发上限」兜底。
>
> **请求体**（camelCase）：`{ "datasetIds": [1,2] }`。`datasetIds` **必须显式非空**（每个 id 为当前用户有权访问的库）；空列表/缺省返回 400——避免下发空 `dataset_ids` claim 被 Python 误判为全库授权造成越权放大。本接口只签发，**不接收 query**（query 在前端直连 Python 时随 stream 请求体提交）。
>
> **响应**：`{ "token": "...", "expiresIn": 30, "streamUrl": "<前端可见的 Python 直连地址>" }`。`streamUrl = RECALL_SESSION_STREAM_BASE_URL + /api/v1/recall/stream`（该路径由 Python 提供，**非** Java 路由）。前端凭 `token`（`Authorization: Bearer`）`POST` 直连 `streamUrl`，请求体含 `config_id`（用户 CHAT 模型），Python 完成召回融合后流式生成答案（事件含 `answer_delta` / `answer_done`）。
>
> **session token claims**（HS256，**独立密钥** `RECALL_SESSION_JWT_SECRET`；与 Python 配置逐字一致）：`iss=tolink-java`、`aud=tolink-rag-frontend`、`scope=recall:stream`、`sub=<user_id 正整数字符串>`、`dataset_ids=<已校验的显式授权范围>`、`iat`、`exp`（默认 30s）。token **短期可复用**，不做 `jti`/一次性/防重放/撤销。
>
> **错误**：未登录 401；`datasetIds` 为空 400；`datasetIds` 越权 `RECALL_SCOPE_FORBIDDEN`（30002，403）；用户禁用 `AUTH_DISABLED`（20003，403）。Python 侧验签失败返回 `401 RECALL_SESSION_UNAUTHORIZED`、越权返回 `403 RECALL_SCOPE_FORBIDDEN`。
