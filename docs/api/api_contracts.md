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
| GET | `/api/v1/admin/feedback` | 管理员反馈列表，支持 `page`、`pageSize`、`status`、`type` |
| GET | `/api/v1/admin/feedback/{id}` | 管理员反馈详情 |
| PATCH | `/api/v1/admin/feedback/{id}/status` | 更新反馈状态：`PENDING` / `PROCESSING` / `RESOLVED` / `CLOSED` |
| PATCH | `/api/v1/admin/feedback/{id}/priority` | 更新反馈优先级：`1` 高 / `2` 中 / `3` 低 |
| PATCH | `/api/v1/admin/feedback/{id}/reply` | 写入管理员回复，不自动修改反馈状态 |
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

> 用量查询（`UsageController`）口径：`llm_usage_log` 自 LINK-184 起为全链路账本，含对话生成（chat 通道）与解析/召回系统侧调用（usage_report 通道）。三个端点新增可选入参 `stage`（默认 `chat`）：缺省/`chat` 仅统计对话用量（与改造前口径一致），`all` 统计全链路，传具体阶段名（`parse`/`recall`）只统计该阶段。`summary`/`daily` 的计数、token、平均延迟均随该过滤生效。`logs` 明细 `UsageLogDTO` 新增 `stage` / `operation` 两字段，供前端区分用量来源。
>
> `configs` 相关响应（`UserLLMConfigDTO`）的能力字段为单数 `capability`（合法取值 `CHAT` / `EMBEDDING` / `SPARSE_EMBEDDING` / `VISION` / `RERANK` / `ASR`，事实来源 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES`），曾误用复数 `capabilities`，前端需按 `capability` 取值。`OCR` 已不再作为独立能力，文档识别类模型应并入 `VISION` 或由执行端按视觉链路处理。
>
> 用户侧 `GET /api/v1/llm/providers`（`ProviderController`）查询启用中的厂商与模型，供用户添加配置前选择，支持按 `capability` 过滤，返回 `ProviderModelDTO`；与管理端 `GET /api/v1/admin/providers`（分页管理视图）区分用途。
>
> 两步配置：`POST /configs/setup-provider`（选厂商 + 填厂商级 Key，按 `llm_provider_model` 展开整厂商「模型×能力」为多条自配并返回列表，重复配置同厂商则更新其 Key）→ `PUT /configs/effective`（按能力选一个启用模型生效，单用户单能力生效唯一）。`PATCH /configs/toggle-model` 独立启停模型（关停后按能力选生效时不展示）。系统预设注册时写入用户配置（`is_system_preset=true`、`is_default=true`），常备只读，仅可经 `PATCH /configs/{id}/default` 按能力切换是否选其生效。`GET /configs` 支持 `capability` / `isActive` 过滤。错误码：删除预设 `10013`、选已关停模型生效 `10012`、模型不支持能力 `10008`、无效能力 `10011`、模型能力缺协议或入口 `10014`、协议非法 `10015`。旧 `POST /configs`、`PATCH /configs/{id}` 已移除（不兼容）。

> **LLM 协议改造字段变更（破坏性 + 加法）**，详见下文「LLM 协议与入口契约」：
> - `GET /api/v1/llm/providers`：`ModelCapabilityDTO.capabilities` 由 `List<String>`（能力名）**升级为** `List<ModelCapabilityDetailDTO>`，每元素为 `{ capability, protocol, apiBaseUrl }`（**破坏性，前端需同批适配**）。`apiBaseUrl` 为**完整端点 URL**（见下「base 形态约定」）。例：`{"modelName":"qwen-max","capabilities":[{"capability":"CHAT","protocol":"openai","apiBaseUrl":"https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"}]}`。
> - `GET /api/v1/llm/configs` / `POST /api/v1/llm/configs/setup-provider`：响应 `UserLLMConfigDTO` 新增 `protocol`（运行快照，复制自模型能力层）；`SetupProviderRequest` 请求体不变。
> - `POST /api/v1/admin/providers/{providerId}/models`：`AddProviderModelRequest` 新增 `protocol`（`NotBlank`，须为 5 协议枚举）、`apiBaseUrl`（`NotBlank`）；缺失或非法分别返回 `10014` / `10015`（400）。
> - `POST /api/v1/admin/providers`：`CreateProviderRequest` 新增 `defaultProtocol`（厂商默认协议模板）。
> - `POST /api/v1/admin/system-presets`：请求体不变；`createPreset` 内部按 (providerId, modelName, capability) 查 `llm_provider_model` 复制 `protocol` / `api_base_url`（事实来源单一，不接受管理员手填）。

### LLM 协议与入口契约

LLM 调用拆成两个正交维度：**`protocol`（API 家族，决定鉴权与请求/响应怎么拼）× `capability`（用途，决定调哪个端点）**。Java 管理端负责把协议与**完整调用端点**落成数据下发，Python RAG 执行端按 `protocol` 选 adapter 后**直接打** `api_base_url`。三层语义（厂商默认模板 / 模型能力事实 / 用户配置快照）与 `protocol` 枚举（`openai` / `anthropic` / `google` / `jina` / `dashscope`）见 `docs/api/mysql_schema.md`「协议与入口三层语义」。

**`api_base_url` 形态约定（2026-06 与 Python PR #192 对齐，语义已反转，两端必须严格一致）**：

- **模型能力层 / 用户配置层** `api_base_url` 存**完整端点 URL**，Python 直打、**不再拼接任何后缀**。例：`https://api.openai.com/v1/chat/completions`、`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`。
- **厂商层** `llm_system_provider.api_base_url` 仍存「协议基地址」，仅作管理端新增模型能力时的表单预填模板，**不参与运行、Python 不读**。
- 端点后缀知识从 Python adapter 移入 **Java seed 生成器**（`scripts/import_ragflow_configs.py` 的 `PROTOCOL_CAPABILITY_SUFFIX`）：完整 URL = 基地址 + `(protocol, capability)` 后缀。新增/改端点只动 Java 数据，adapter 零改动。
- **唯一例外 `google`**：Gemini 原生流式需把 `:generateContent` 换成 `:streamGenerateContent?alt=sse`（流式开关编码在 URL 里，无法用单条静态 URL 表达），故 `google` 仍下发 base 到 `/v1beta`，由 Python 按 google 规则补全路径与流式后缀，鉴权用 `x-goog-api-key`。（`dashscope` ASR 异步轮询同类问题，本期不做，暂存 base。）

**完整端点 URL 对照表（= 下发给 Python 的 `api_base_url` 值）**：

| protocol | capability | api_base_url（完整端点，`google` 除外） |
| --- | --- | --- |
| `openai` | CHAT / VISION | `{base}/chat/completions`，如 `https://api.openai.com/v1/chat/completions` |
| `openai` | EMBEDDING / SPARSE_EMBEDDING | `{base}/embeddings` |
| `openai` | ASR | `{base}/audio/transcriptions`（whisper 同步） |
| `anthropic` | CHAT / VISION | `https://api.anthropic.com/v1/messages` |
| `google` | CHAT / EMBEDDING / SPARSE_EMBEDDING / VISION | **例外**：base `https://generativelanguage.googleapis.com/v1beta`，由 Python 补全 |
| `jina` | RERANK | `https://api.jina.ai/v1/rerank` |
| `jina` | EMBEDDING / SPARSE_EMBEDDING | `https://api.jina.ai/v1/embeddings` |
| `dashscope` | RERANK | `https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank`（千问原生嵌套） |
| `dashscope` | ASR | 本期不做，暂存 base `https://dashscope.aliyuncs.com/api/v1` |

**adapter dispatch 契约**：

- 执行端按 `(protocol, capability)` 二维选 adapter，**不依据 `provider_type`**；`provider_type` 仅作厂商身份、展示、审计保留。
- adapter 职责 = 3 件套：① 拼鉴权头 ② 构建请求体 ③ 解析回包；**URL 直接用 `api_base_url`，不再拼后缀**（`google` 例外按协议补全）。
- 未知 `(protocol, capability)` 组合返回明确错误，不回退猜测。本期 Python 实际落地组合：`openai`+CHAT/EMBEDDING、`anthropic`+CHAT、`google`+CHAT、`jina`+RERANK/EMBEDDING、`dashscope`+RERANK；VISION/SPARSE_EMBEDDING/ASR schema 与 seed 已支持，Python 端需后续对接具体 adapter 与请求/响应协议。
- 职责边界：完整端点 URL（多变的「去哪」）= 数据，Java 管；鉴权 + 请求体 + 回包解析（稳定的「怎么调」）= 代码，adapter 管。新增同协议厂商 Java 加一行数据即可，adapter 零改动。

## Chat

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/chat/conversations` | 创建会话（前端在发起 `/rag/stream` 问答前先建会话取 `conversation_id`） |
| GET | `/api/v1/chat/conversations` | 会话列表 |
| GET | `/api/v1/chat/conversations/{id}/messages` | 消息列表（一行一轮：`query` / `answer` / `references` / `status`） |
| PATCH | `/api/v1/chat/conversations/{id}` | 更新会话 |
| DELETE | `/api/v1/chat/conversations/{id}` | 删除会话 |

> 写入消息接口 `POST /api/v1/chat/conversations/{id}/messages` 已下线：对话轮次改由 Python 问答执行器经 `tolink.rag.chat_turn` 上报、Java 单事务落库 `chat_message` / `llm_usage_log` / `chat_conversation`（见 `docs/api/mq_contracts.md`）。前端职责简化为先创建会话拿到 `conversation_id`，随 `/api/v1/rag/stream` 请求带上。
>
> 会话标题由首轮 `query` 生成（超长按 30 字符截断加省略号；命中标题唯一约束 `(user_id, dataset_id, title)` 冲突时保留默认标题），不再依赖已下线的写消息接口。

## Dataset / Document File

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/datasets` | 创建数据集 |
| GET | `/api/v1/datasets` | 数据集列表 |
| GET | `/api/v1/datasets/{datasetId}` | 数据集详情 |
| PATCH | `/api/v1/datasets/{datasetId}` | 更新数据集 |
| DELETE | `/api/v1/datasets/{datasetId}` | 删除数据集 |
| GET | `/api/v1/datasets/{datasetId}/parse-config` | 读取数据集解析/检索配置（回显已存；`recall` 新增项缺失时补默认） |
| PUT | `/api/v1/datasets/{datasetId}/parse-config` | 全量保存数据集解析/检索配置（整页保存，整行四类覆盖） |
| POST | `/api/v1/datasets/{datasetId}/files` | 上传文档文件（异步：立即返回 `uploadStatus=UPLOADING`） |
| GET | `/api/v1/datasets/{datasetId}/files` | 文件列表（支持按 `uploadStatus` 过滤，前端据此轮询上传终态） |
| GET | `/api/v1/files/recent` | 当前用户全局最近文档列表 |
| GET | `/api/v1/files/{fileId}` | 文件详情 |
| DELETE | `/api/v1/files/{fileId}` | 删除文件 |
| POST | `/api/v1/files/{fileId}/parse` | 提交解析 |
| GET | `/api/v1/datasets/{datasetId}/files/parse-results` | 解析结果列表 |

> 文档上传异步化：`POST .../files` 在同步校验（鉴权/数据集归属/格式/大小/文件名/同名）通过后立即返回 `uploadStatus=UPLOADING`；OSS 上传与终态回写（`UPLOAD_SUCCESS`/`UPLOAD_FAILED`）在后台线程池异步完成。同步校验失败仍即时返回 4xx（未登录/无权 401-404、格式/大小/文件名/同名 400）。前端需按 `uploadStatus` 轮询 list/detail 获取终态。同名重试：撞到 `UPLOAD_FAILED` 同名文件会复用原记录重传，撞到 `UPLOADING`/`UPLOAD_SUCCESS` 返回 400。

> 解析/检索配置（`/parse-config`，LINK-149/LINK-170）：请求/响应为四类嵌套结构 `{chunking, enhancement, pdf, recall}`，字段名 snake_case，与 Python `dataset_config` Pydantic 模型对齐（15 项：chunking 3 / enhancement 2 开关 / pdf 1 / recall 9）。Java 只做存/改/回显，**PUT 为整行全量覆盖**（前端整页保存）。`recall` 新增 3 项会在读取时补齐默认：`recall_enabled_sources=["bm25","sparse","dense"]`、`rerank_top_n=8`、`recall_strict=false`；创建数据集时同步写入默认配置行。关键校验即时拦截 400：`overlap_tokens` 0–64、`min_candidate_chunk_tokens` 128–256、`pdf_parser_backend` ∈ {`auto`,`mineru`,`opendataloader`,`naive`}；recall 历史 6 项范围不在 Java 拦截，LINK-170 新增项中 `recall_enabled_sources` 仅允许 `bm25`/`sparse`/`dense`（写入去空白、去空项、去重，允许空数组），`rerank_top_n` 必须为正整数，`recall_strict` 为布尔。增强仅两个开关（`enable_table_enhancement`/`enable_image_enhancement`），历史残留的 `table_model`/`vision_model` 落库时丢弃；增强模型由 Python 取发起用户默认 CHAT/VISION（依赖 LINK-148 PR #190）。越权/不存在 404、未登录 401。

## OSS / Internal

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/oss-files/{bizType}` | 通用 OSS 上传 |
| GET | `/api/v1/internal/files/{fileId}/content` | Python 端读取私有文件内容 |

## Feedback

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/feedback` | 匿名提交反馈，`multipart/form-data`，可选 `file` 附件 |

`POST /api/v1/feedback` 接收 `type`（可选，默认 `OTHER`）、`title`（必填，最长 128）、`content`（必填，最长 5000）和可选 `file`。附件上传到公开桶 `tolink-public`，数据库只保存 `attachmentObjectKey`（object key 形如 `feedback/yyyy/MM/{uuid}.{suffix}`，精度到月）；响应 `FeedbackDTO` 同时返回 `attachmentObjectKey` 与可直接访问的 `attachmentUrl`（由后端按公开桶 endpoint + bucket + key 拼装，无附件时为空）。管理端 `/api/v1/admin/feedback` 列表与详情同样返回 `attachmentUrl`。

## Blog

管理端接口全部要求 `ADMIN` 角色；公开端无需登录。管理列表和公开列表均不返回 Markdown 正文，管理详情和公开详情才从私有 OSS 对象读取正文。

前端联调细节见 [Blog Frontend Integration](../../.specs/blog/blog_frontend_integration.md)。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/blog/posts` | 管理端文章列表，可按 `status` 过滤，不含正文 |
| GET | `/api/v1/admin/blog/posts/{postId}` | 管理端文章详情，含 Markdown 正文 |
| POST | `/api/v1/admin/blog/posts` | 创建草稿 |
| PATCH | `/api/v1/admin/blog/posts/{postId}` | 更新标题、摘要或封面资源；`slug` 不允许手动更新 |
| POST | `/api/v1/admin/blog/posts/{postId}/content/import` | 导入 `.md` / `.markdown` 草稿；正文图片自动入 PUBLIC OSS 并改写 Markdown 引用 |
| PUT | `/api/v1/admin/blog/posts/{postId}/content` | 保存编辑器当前完整 Markdown 正文；支持自动保存 |
| POST | `/api/v1/admin/blog/posts/{postId}/publish` | 发布文章 |
| POST | `/api/v1/admin/blog/posts/{postId}/unpublish` | 下架文章 |
| DELETE | `/api/v1/admin/blog/posts/{postId}` | 软删文章，不删除 OSS 对象 |
| GET | `/api/v1/admin/blog/posts/{postId}/assets` | 查询文章未删除图片资源，支持 `assetType` 筛选 |
| POST | `/api/v1/admin/blog/posts/{postId}/assets` | 上传 `COVER` 封面图片或 `CONTENT_IMAGE` 正文图片；正文图片返回 `markdownText` |
| DELETE | `/api/v1/admin/blog/posts/{postId}/assets/{assetId}` | 删除资源；正文图片仍被当前 Markdown 引用时拒绝 |
| GET | `/api/v1/blog/posts` | 公开文章列表，只返回已发布文章，不含正文 |
| GET | `/api/v1/blog/posts/{slug}` | 公开文章详情，含 Markdown 正文 |

创建草稿时后端生成去掉连字符的 32 位小写 UUID 作为 `slug`，前端不提交也不更新 `slug`。不提供 `/api/v1/admin/blog/posts/{postId}/content/download` 下载路由。正文使用私有 OSS UUID Key，替换正文时先上传新对象，再切换 `blog_post.content_object_key`。Markdown 正文中的图片引用由后端自动处理：可成功下载的 `http` / `https` 图片会下载后写入 PUBLIC OSS 并记录为 `blog_asset.CONTENT_IMAGE`，`data:image/*;base64` 图片会解码后写入 PUBLIC OSS 并记录资源，随后 Markdown 中的图片地址替换为公开 URL；网络图片下载失败、超时、大小超限、类型不允许或安全校验失败时保留原 URL，不阻断导入/保存；单独 `.md` 上传无法携带本地相对路径图片，因此相对路径图片会返回 400。

统一响应模型为 `Result<T>`，分页模型为 `PageResult<T>`。

解析终态结果由 Python 写入共享数据库，Java 不再消费 `tolink.rag.parse_result`，也不再提供解析 SSE 事件订阅或过程事件回调入口；解析结果查询读取 `document_parse_file.latest_parse_task_id` 所指向的日志状态。

## Recall

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/recall/sessions` | 签发前端直连 Python 召回的短期 session token（普通 JSON） |

> **历史链路已下线（LINK-122）**：Java 曾提供 `POST /api/v1/recall/stream` 中转代理（同步转发 Python 内部端点 `/api/v1/internal/recall/stream`），现已废弃移除。聊天召回统一走「前端直连 Python」：前端凭下方接口签发的 session token 直连 `streamUrl`（即 `<rag-host>/api/v1/rag/stream`，由 Python 提供——**LINK-138**：Python LINK-131 已将该对外端点由 `/api/v1/recall/stream` 改名、语义升级为 RAG 流式问答），Java **不在**召回/生成请求路径上。

### POST /api/v1/recall/sessions（前端直连签发）

> 「前端直连 Python 召回 SSE」链路（LINK-104）：Java 只做 Sa-Token 鉴权 + 用户状态（`status==1`）+ `datasetIds` 归属校验，签发短期 HS256 session token；**不代理/中转 SSE 流内容**，资源滥用由 Python「按用户并发上限」兜底。
>
> **请求体**（camelCase）：`{ "datasetIds": [1,2] }`。`datasetIds` **必须显式非空**（每个 id 为当前用户有权访问的库）；空列表/缺省返回 400——避免下发空 `dataset_ids` claim 被 Python 误判为全库授权造成越权放大。本接口只签发，**不接收 query**（query 在前端直连 Python 时随 stream 请求体提交）。
>
> **响应**：`{ "token": "...", "expiresIn": 30, "streamUrl": "<前端可见的 Python 直连地址>" }`。`streamUrl = RECALL_SESSION_STREAM_BASE_URL + /api/v1/rag/stream`（该路径由 Python 提供，**非** Java 路由）。前端凭 `token`（`Authorization: Bearer`）`POST` 直连 `streamUrl`，请求体为 `{ "query": "...", "config_id": <CHAT 模型配置 id>, "dataset_ids"?: [..] }`（`config_id` 必填）；Python 完成召回融合后流式生成答案：逐 token `event: answer_delta` → 终态 `event: answer_done`，**空命中**走 `event: recall_done`，失败 `event: error`。
>
> **session token claims**（HS256，**独立密钥** `RECALL_SESSION_JWT_SECRET`；与 Python 配置逐字一致）：`iss=tolink-java`、`aud=tolink-rag-frontend`、`scope=recall:stream`、`sub=<user_id 正整数字符串>`、`dataset_ids=<已校验的显式授权范围>`、`iat`、`exp`（默认 30s）。token **短期可复用**，不做 `jti`/一次性/防重放/撤销。
>
> **错误**：未登录 401；`datasetIds` 为空 400；`datasetIds` 越权 `RECALL_SCOPE_FORBIDDEN`（30002，403）；用户禁用 `AUTH_DISABLED`（20003，403）。Python 侧验签失败返回 `401 RECALL_SESSION_UNAUTHORIZED`、越权返回 `403 RECALL_SCOPE_FORBIDDEN`。
