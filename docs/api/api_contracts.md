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
| POST | `/api/v1/user/avatar` | 上传并更新当前用户头像 |
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
| GET | `/api/v1/admin/providers` | 管理端厂商列表（分页，按优先级倒序，返回 `iconUrl` / `iconObjectKey`） |
| POST | `/api/v1/admin/providers` | 创建系统厂商（`CreateProviderRequest`，含 `defaultProtocol` / `iconUrl` / `iconObjectKey`；直接创建为启用状态要求已有上架模型，否则返回 `10019`） |
| POST | `/api/v1/admin/providers/icon` | 上传厂商图标到公开 OSS，返回图标 URL 与 object key |
| PATCH | `/api/v1/admin/providers/{id}` | 部分更新厂商字段，支持更新/清空 `iconUrl` / `iconObjectKey`，变更后双删缓存；更新为启用状态要求已有上架模型，否则返回 `10019` |
| DELETE | `/api/v1/admin/providers/{id}` | 删除系统厂商 |
| PATCH | `/api/v1/admin/providers/{id}/active` | 启用/禁用厂商（`isActive` 查询参数）；启用要求已有上架模型，否则返回 `10019` |
| GET | `/api/v1/admin/provider-models` | 管理端模型能力目录分页（可按 `providerId` / `capability` / `isActive` 过滤，含下架项） |
| POST | `/api/v1/admin/providers/{providerId}/models` | 新增厂商模型能力目录项 |
| PATCH | `/api/v1/admin/provider-models/{id}` | 部分更新模型能力目录项（模型名、能力、协议、入口、上下架状态） |
| DELETE | `/api/v1/admin/provider-models/{id}` | 删除模型能力目录项 |
| PATCH | `/api/v1/admin/provider-models/{id}/active` | 上/下架模型能力目录项 |
| POST | `/api/v1/admin/providers/{providerId}/model-sync` | 手动刷新外部模型目录候选（当前支持 `MODELS_DEV`，只写候选表） |
| GET | `/api/v1/admin/model-sync-jobs` | 外部模型目录刷新任务分页，支持 `providerId` / `syncSource` / `status` 过滤 |
| GET | `/api/v1/admin/model-sync-candidates` | 外部模型候选分页，支持 `providerId` / `jobId` / `reviewStatus` / `capability` 过滤 |
| POST | `/api/v1/admin/model-sync-candidates/{id}/publish` | 将外部候选发布到正式 `llm_provider_model`，请求体可覆盖模型名/展示名/能力/协议/入口 |
| PATCH | `/api/v1/admin/model-sync-candidates/{id}/review` | 更新外部候选审核状态（`PENDING` / `REJECTED`） |
| GET | `/api/v1/admin/system-presets` | 系统预设列表（平台 Key 脱敏） |
| POST | `/api/v1/admin/system-presets` | 新增 LinkRag 系统预设（支持手动填写或从正式模型目录快捷加入，平台 Key 加密入库） |
| PATCH | `/api/v1/admin/system-presets/{id}` | 部分更新系统预设（支持手动更新运行事实或从正式模型目录重新快捷复制） |
| PATCH | `/api/v1/admin/system-presets/{id}/active` | 启用/禁用系统预设（当前默认需先指定替代项） |
| PATCH | `/api/v1/admin/system-presets/{id}/default` | 设为该能力的 LinkRag 系统兜底默认 |
| DELETE | `/api/v1/admin/system-presets/{id}` | 删除系统预设 |

`POST /api/v1/user/avatar` 使用 `multipart/form-data`，字段名为 `file`。后端按 OSS `avatar` 业务规则校验：仅允许 `jpg` / `jpeg` / `png` / `gif` / `webp`，最大 5MB，写入公开 OSS（MinIO 部署时为 public bucket），object key 形如 `avatar/{userId}/{uuid}.{suffix}`。上传成功后将公开访问地址写入 `sys_user.avatar_url`，响应为更新后的 `UserProfileDTO`。

`POST /api/v1/admin/providers/icon` 使用 `multipart/form-data`，字段名为 `file`。后端按 OSS `providerIcon` 业务规则校验：仅允许 `jpg` / `jpeg` / `png` / `gif` / `webp`，最大 5MB，写入公开 OSS，object key 形如 `providerIcon/{uuid}.{suffix}`。上传成功后返回 `ProviderIconUploadDTO{ iconUrl, iconObjectKey }`；前端再将两者传给 `POST /api/v1/admin/providers` 或 `PATCH /api/v1/admin/providers/{id}`，分别写入 `llm_system_provider.icon_url` 与 `icon_object_key`。用户侧 `GET /api/v1/llm/providers` 返回可添加厂商的 `iconUrl`；`GET /api/v1/llm/configs` 的 LinkRag 只读配置项也返回 `iconUrl`，来源同为 `llm_system_provider`，用于替代前端硬编码厂商图标。

## LLM

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/llm/providers` | 可用厂商与模型能力（来源 `llm_provider_model`） |
| GET | `/api/v1/llm/configs` | 用户可用配置列表（用户自配 + LinkRag 只读配置） |
| POST | `/api/v1/llm/configs/setup-provider` | 配置厂商：选厂商+填厂商级 Key，自动展开整厂商模型 |
| PATCH | `/api/v1/llm/configs/toggle-model` | 模型/能力启停（`capability` 可选：有则单能力，无则按模型批量） |
| PUT | `/api/v1/llm/configs/effective` | 按能力选生效模型 |
| GET | `/api/v1/llm/configs/default` | 取某能力实际生效配置（用户自配优先，缺省回退 LinkRag 系统预设） |
| PATCH | `/api/v1/llm/configs/{id}/default` | 设某能力用户自配生效 |
| PATCH | `/api/v1/llm/configs/default/system` | 兼容接口：清空某能力用户自配默认，恢复 LinkRag 配置 |
| DELETE | `/api/v1/llm/configs/{id}` | 删除用户自配配置 |
| GET | `/api/v1/llm/usage/summary` | 用量汇总 |
| GET | `/api/v1/llm/usage/daily` | 日度用量 |
| GET | `/api/v1/llm/usage/logs` | 用量明细 |
| GET | `/api/v1/llm/usage/by-model` | 按厂商+模型聚合 |
| GET | `/api/v1/llm/usage/trend` | 用量环比趋势 |

> 用量查询（`UsageController`）口径：`llm_usage_log` 自 LINK-184 起为全链路账本，含对话生成（chat 通道）与解析/召回系统侧调用（usage_report 通道）。`summary`/`daily`/`logs` 新增可选入参 `stage`（默认 `chat`）：缺省/`chat` 仅统计对话用量（与改造前口径一致），`all` 统计全链路，传具体阶段名（`parse`/`recall`）只统计该阶段。`summary`/`daily` 的计数、token、平均延迟均随该过滤生效。`logs` 明细 `UsageLogDTO` 新增 `stage` / `operation` 两字段，供前端区分用量来源。
>
> `summary`（`UsageSummaryDTO`）扩展（LINK-182）：新增 `successCalls` / `failedCalls` / `successRate`（0~1，无调用为 0）；`averageLatencyMs` 口径改为**仅成功调用**（`status='success'`，大小写不敏感），避免失败/超时拉偏均值。
>
> `GET /by-model`（`List<ModelUsageDTO>`，LINK-182）：按「`provider_type` + `model_name`」SQL 聚合（`COUNT`/`SUM`），按 `totalTokens` 降序，无数据返回 `[]`。字段：`providerType`/`modelName`/`calls`/`promptTokens`/`completionTokens`/`totalTokens`。
>
> `GET /trend`（`UsageTrendDTO`，LINK-182）：当前周期 vs 紧邻的等长上一周期环比。字段：`currentTokens`/`previousTokens`/`currentCalls`/`previousCalls`/`tokenGrowthRate`/`callGrowthRate`。增长率为小数（`0.18`=+18%），**上一周期为 0（无可比基数）时为 `null`**（前端显示「—」，非 +∞）。
>
> `by-model` 与 `trend` 为**全链路口径**（不按 stage 过滤，反映全部模型/总体趋势），与 `summary`/`daily`/`logs` 默认仅 `chat` 的口径不同——展示侧若需对齐，对 `summary` 传 `stage=all`。入参 `startDate`/`endDate` 同为 `yyyy-MM-dd`（含端）；均 `@SaCheckLogin` 且按登录用户隔离。
>
> `configs` 相关响应（`UserLLMConfigDTO`）的能力字段为单数 `capability`（合法取值 `CHAT` / `EMBEDDING` / `SPARSE_EMBEDDING` / `VISION` / `RERANK` / `ASR`，事实来源 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES`），曾误用复数 `capabilities`，前端需按 `capability` 取值。`OCR` 已不再作为独立能力，文档识别类模型应并入 `VISION` 或由执行端按视觉链路处理。
>
> 用户侧 `GET /api/v1/llm/providers`（`ProviderController`）查询启用中的厂商与模型，供用户添加配置前选择，支持按 `capability` 过滤，返回 `ProviderModelDTO`（含 `iconUrl`）；与管理端 `GET /api/v1/admin/providers`（分页管理视图）区分用途。
> `provider_type=linkrag` 是系统服务厂商：不出现在用户侧可添加厂商列表，用户也不能调用 `setup-provider` 配置它的 Key（返回 `SYSTEM_PROVIDER_READONLY(10016/400)`）；但会作为 `GET /configs` 的只读配置项返回，用户可以选择使用。LinkRag 图标仍存于 `llm_system_provider.icon_url`，`llm_system_preset` 不重复保存厂商图标；主种子脚本只创建 LinkRag 厂商行，不向 `llm_provider_model` 写入 LinkRag 模型，LinkRag 模型只在 `llm_system_preset` 中作为系统兜底默认维护。
>
> 管理端新增 LinkRag 兜底模型统一使用 `POST /api/v1/admin/system-presets`，最终落库恒为 `provider_type=linkrag`、`provider_id=LinkRag 厂商 ID`。接口支持两种方式：手动填写 `{ modelName, displayName?, capability, protocol, apiBaseUrl, apiKey, isDefault? }`；或快捷加入正式模型目录 `{ sourceProviderModelId, apiKey, isDefault? }`，后端从 `llm_provider_model` 复制模型名、展示名、能力、协议和完整入口。兼容旧入参 `{ providerId, modelName, capability, apiKey }`，其语义也是“从该源厂商模型目录复制到 LinkRag 预设”，不是把系统预设归属到源厂商。`apiKey` 是平台 Key，用户无需配置 Key。
>
> 两步配置：`POST /configs/setup-provider`（选厂商 + 填厂商级 Key，按 `llm_provider_model` 展开整厂商「模型×能力」为多条自配并返回列表，重复配置同厂商则更新其 Key）→ `PUT /configs/effective`（按能力选一个启用模型生效，单用户单能力唯一）。`providerType=linkrag` 时，`PUT /configs/effective` 清空该能力用户自配默认，使 LinkRag 只读配置生效。`PATCH /configs/toggle-model` 独立启停用户自配配置：请求体 `capability` 存在时只启停该 `providerType + modelName + capability` 自配行，不存在时兼容旧前端，按 `providerType + modelName` 批量启停该模型全部用户自配能力；若关闭的是当前能力用户默认配置，后端清除该默认标记，使实际生效配置回退 LinkRag 系统默认。LinkRag 不可编辑/删除/启停。`GET /configs/default` 返回 `EffectiveLLMConfigDTO`，字段 `source` 为 `USER` 或 `SYSTEM`，供执行端按来源表读取；前端配置页优先使用 `GET /configs` 的 `isDefault` 展示当前生效项。`GET /configs` 支持 `capability` / `isActive` 过滤，返回用户自配配置 + LinkRag 只读配置；`UserLLMConfigDTO.isEditable=false` 表示只读，不允许编辑、删除、启停或改 Key。错误码：系统服务厂商不可自配/启停 `10016`、能力级启停找不到用户自配配置 `10004`、选已关停模型生效 `10012`、模型不支持能力 `10008`、无效能力 `10011`、模型能力缺协议或入口 `10014`、协议非法 `10015`。旧 `POST /configs`、`PATCH /configs/{id}` 已移除（不兼容）。

> **LLM 协议改造字段变更（破坏性 + 加法）**，详见下文「LLM 协议与入口契约」：
> - `GET /api/v1/llm/providers`：`ModelCapabilityDTO` 新增 `displayName`（模型短展示名，真实调用仍用 `modelName`）；`capabilities` 由 `List<String>`（能力名）**升级为** `List<ModelCapabilityDetailDTO>`，每元素为 `{ capability, protocol, apiBaseUrl }`（**破坏性，前端需同批适配**）。`apiBaseUrl` 为**完整端点 URL**（见下「base 形态约定」）。例：`{"modelName":"Qwen/Qwen3.6-27B","displayName":"Qwen 3.6 27B","capabilities":[{"capability":"VISION","protocol":"openai","apiBaseUrl":"https://api.siliconflow.cn/v1/chat/completions"}]}`。
> - `GET /api/v1/llm/configs` / `POST /api/v1/llm/configs/setup-provider`：响应 `UserLLMConfigDTO` 新增 `displayName`、`protocol`（运行快照，复制自模型能力层）与 `isEditable`（LinkRag 为 `false`）；其中 `GET /configs` 的 LinkRag 只读项会额外返回来自系统厂商表的 `iconUrl`；`SetupProviderRequest` 请求体不变。
> - `POST /api/v1/admin/providers/{providerId}/models`：`AddProviderModelRequest` 新增可选 `displayName`、必填 `protocol`（`NotBlank`，须为 5 协议枚举）、`apiBaseUrl`（`NotBlank`）；缺失或非法分别返回 `10014` / `10015`（400）。
> - `POST /api/v1/admin/providers`：`CreateProviderRequest` 新增 `defaultProtocol`（厂商默认协议模板）。
> - `GET /api/v1/llm/configs/default`：响应由 `UserLLMConfigDTO` 改为 `EffectiveLLMConfigDTO`，新增 `source`、`configId` 与 `displayName`，用于 Python 按来源表读取最终配置。
> - `POST /api/v1/admin/system-presets`：新增可选 `isDefault`；支持手动填写 `protocol` / `apiBaseUrl`，也支持按 `sourceProviderModelId` 或兼容字段 `(providerId, modelName, capability)` 从正式目录快捷复制 `protocol` / `api_base_url` / `display_name`。无论来源如何，预设都归属 LinkRag 系统厂商。当 `isDefault=true` 时自动解除同能力其他 LinkRag 系统默认。
> - 外部模型目录刷新（LINK-50）：`POST /api/v1/admin/providers/{providerId}/model-sync` 只把 `models.dev` 等外部源数据写入 `llm_provider_model_sync_job` / `llm_provider_model_sync_candidate`，不直接影响用户侧模型列表；管理员审核后调用 `POST /api/v1/admin/model-sync-candidates/{id}/publish` 才会复用正式目录服务写入 `llm_provider_model` 并清理相关缓存。候选响应包含外部源模型发布日期 `releaseDate`；候选发布可覆盖推断出的 `capability` / `protocol` / `apiBaseUrl`，避免外部源误判直接进入运行目录。候选响应中的 `capability` 是兼容别名，等同真实推断字段 `inferredCapability`。重复刷新同一厂商时，后端按 `(providerId, syncSource, modelName, inferredCapability)` 更新既有候选，不再追加重复行。

### LLM 协议与入口契约

LLM 调用拆成两个正交维度：**`protocol`（API 家族或专用 adapter，决定鉴权与请求/响应怎么拼）× `capability`（用途，决定调哪个端点）**。Java 管理端负责把协议与**完整调用端点**落成数据下发，Python RAG 执行端按 `protocol` 选 adapter 后**直接打** `api_base_url`。三层语义（厂商默认模板 / 模型能力事实 / 用户配置快照）与 `protocol` 枚举（`openai` / `anthropic` / `google` / `jina` / `dashscope` / `bge_m3` / `doubao_vision`）见 `docs/api/mysql_schema.md`「协议与入口三层语义」。

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
| `bge_m3` | SPARSE_EMBEDDING | 完整服务地址，如 `http://103.205.254.30:37997/encode`，Python 直打并发送 `{ return_dense: false, return_sparse: true }` |
| `doubao_vision` | SPARSE_EMBEDDING | `https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal`，Python 逐条请求火山多模态 embedding |

**adapter dispatch 契约**：

- 执行端按 `(protocol, capability)` 二维选 adapter，**不依据 `provider_type`**；`provider_type` 仅作厂商身份、展示、审计保留。
- adapter 职责 = 3 件套：① 拼鉴权头 ② 构建请求体 ③ 解析回包；**URL 直接用 `api_base_url`，不再拼后缀**（`google` 例外按协议补全）。
- 未知 `(protocol, capability)` 组合返回明确错误，不回退猜测。本期 Python 实际落地组合：`openai`+CHAT/VISION/EMBEDDING/ASR、`anthropic`+CHAT/VISION、`google`+CHAT/VISION/EMBEDDING、`jina`+RERANK/EMBEDDING、`dashscope`+RERANK/ASR、`bge_m3`+SPARSE_EMBEDDING、`doubao_vision`+SPARSE_EMBEDDING。
- 职责边界：完整端点 URL（多变的「去哪」）= 数据，Java 管；鉴权 + 请求体 + 回包解析（稳定的「怎么调」）= 代码，adapter 管。新增同协议厂商 Java 加一行数据即可，adapter 零改动。

## Chat

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/chat/conversations` | 创建会话（前端在发起 `/rag/stream` 问答前先建会话取 `conversation_id`） |
| GET | `/api/v1/chat/conversations` | 会话列表（按 `updated_at` 倒序，置顶优先） |
| GET | `/api/v1/chat/conversations/{id}/messages` | 消息列表（一行一轮：`turnId` / `query` / `answer` / `references` / `requestId` / `status` / `errorCode` / `errorMessage`，按 `createdAt` 正序分页，含在途 `GENERATING`） |
| PATCH | `/api/v1/chat/conversations/{id}` | 更新会话 |
| DELETE | `/api/v1/chat/conversations/{id}` | 删除会话 |
| POST | `/api/v1/knowledge/chunks/batch` | 批量查询当前用户可访问的 ACTIVE Chunk 详情，用于历史消息按 `references` 恢复召回片段卡片 |

> 写入消息接口 `POST /api/v1/chat/conversations/{id}/messages` 已下线：对话轮次改由 Python 问答执行器经 `tolink.rag.chat_turn` 上报、Java 按 `turn_id` upsert 落库 `chat_message` / `chat_conversation`（generate 用量自 LINK-191 起改走 `tolink.rag.usage_report` 落 `llm_usage_log`，见 `docs/api/mq_contracts.md`）。前端职责简化为先创建会话拿到 `conversation_id`，随 `/api/v1/rag/stream` 请求带上。
>
> 消息列表（`MessageDTO`）暴露轮次状态供前端重载判定（chat-stream-resilient-persist）：`status` = `GENERATING` / `COMPLETED` / `FAILED`，`errorCode` / `errorMessage` 仅 `FAILED` 非空，`turnId` 为前端每轮稳定 UUID（轮次幂等键）。列表**返回在途 `GENERATING` 消息**（带部分 `answer`），不做状态过滤，按 `createdAt` 升序。
>
> 会话创建时默认标题为“新对话”；对话标题由 Python 问答链路生成并随 `tolink.rag.chat_turn.title` 上报。Java 不再发起标题 LLM 调用，也不再用首问生成临时标题；仅在当前标题为空或仍为默认“新对话”时落库上游标题，用户已手动改成其它标题则跳过，不覆盖。
>
> `POST /api/v1/knowledge/chunks/batch` 请求体为 `{"chunkIds":["chunk-1","chunk-2"]}`，单次最多 100 个。响应 `data` 数组元素含 `chunkId` / `documentId` / `fileName` / `content` / `score`；当前历史现查不保存召回分数，`score` 为 `null`。接口按当前登录用户过滤 `kb_document_chunk.user_id` 且只返回 `lifecycle_status='ACTIVE'`、正文非空的片段；不存在、越权或已移除的 chunk 会被跳过，返回顺序与请求中的可返回 chunk 顺序一致。

## Dataset / Document File

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/datasets` | 创建数据集（必须绑定稀疏/稠密向量模型配置） |
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

> 创建数据集：`POST /api/v1/datasets` 请求体除 `name`/`description` 外，必须提供 `sparse_embedding_config_id` 与 `dense_embedding_config_id`。两者分别指向当前用户启用中的 `llm_user_config.id`，能力必须为 `SPARSE_EMBEDDING` / `EMBEDDING`；不存在、停用、越权或能力不匹配均返回 400。创建成功时 Java 同步写入 `dataset_parse_config` 默认行并固化这两个绑定，后续解析构建向量与召回都以该数据集绑定为准，不再按用户“当前默认模型”漂移。

> 解析/检索配置（`/parse-config`，LINK-219）：请求/响应为 `{sparse_embedding_config_id, dense_embedding_config_id, chunking, enhancement, pdf, recall}`，字段名 snake_case，与 Python `dataset_config` Pydantic 模型对齐（JSON 配置 27 项：chunking 7 / enhancement 3 开关 / pdf 1 / recall 14；向量模型绑定 2 项为表列）。Java 只做存/改/回显，**PUT 为整行全量覆盖四类 JSON 配置**；两个模型绑定字段已有值后不可修改，未传则保留原绑定，首次为无配置行创建时必须能解析出完整绑定。`recall` 兼容旧 JSON 缺失项时读取补齐默认：`recall_enabled_sources=["bm25","sparse","dense"]`、`rerank_top_n=8`、`recall_strict=false`。关键校验即时拦截 400：模型绑定必须属于当前用户且能力匹配；`overlap_tokens` 0-64、`min_candidate_chunk_tokens` 128-256、`max_chunk_tokens` 256-2048、`hard_max_tokens` 512-8192，且 `max_chunk_tokens >= min_candidate_chunk_tokens`、`hard_max_tokens >= max_chunk_tokens`，`stage_two_algorithm` 仅支持 `noop`/`semantic_depth_window`（写入前 trim/lower）；`pdf_parser_backend` ∈ {`auto`,`mineru`,`opendataloader`,`naive`}；`recall_result_limit`、`bm25_top_k`、`sparse_top_k`、`dense_top_k`、`rerank_top_n` 必须为正整数，`sparse_score_threshold`、`dense_score_threshold`、`fusion_bm25_weight`、`fusion_sparse_weight`、`fusion_dense_weight` 必须为非负有限数，`recall_fusion_strategy` 仅支持 `rrf`/`weighted_score`（写入前 trim/lower），`recall_enabled_sources` 仅允许 `bm25`/`sparse`/`dense`（写入去空白、去空项、去重，允许空数组），`recall_strict` 为布尔。增强三个开关（`enable_table_enhancement`/`enable_image_enhancement`/`enable_heading_hierarchy`），历史残留的 `table_model`/`vision_model` 落库时丢弃；增强模型由 Python 取发起用户默认 CHAT/VISION（依赖 LINK-148 PR #190）。越权/不存在 404、未登录 401。

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

> 「前端直连 Python 召回 SSE」链路（LINK-104）：Java 只做 Sa-Token 鉴权 + 用户状态（`status==1`）+ `datasetIds` 归属校验 + 数据集稀疏/稠密向量模型绑定校验，签发短期 HS256 session token；**不代理/中转 SSE 流内容**，资源滥用由 Python「按用户并发上限」兜底。历史数据集若缺少任一绑定，或绑定的用户配置已停用/删除/能力不匹配，拒绝签发。
>
> **请求体**（camelCase）：`{ "datasetIds": [1,2] }`。`datasetIds` **必须显式非空**（每个 id 为当前用户有权访问的库）；空列表/缺省返回 400——避免下发空 `dataset_ids` claim 被 Python 误判为全库授权造成越权放大。本接口只签发，**不接收 query**（query 在前端直连 Python 时随 stream 请求体提交）。
>
> **响应**：`{ "token": "...", "expiresIn": 30, "streamUrl": "<前端可见的 Python 直连地址>" }`。`streamUrl = RECALL_SESSION_STREAM_BASE_URL + /api/v1/rag/stream`（该路径由 Python 提供，**非** Java 路由）。前端凭 `token`（`Authorization: Bearer`）`POST` 直连 `streamUrl`，请求体为 `{ "query": "...", "config_id": <CHAT 模型配置 id>, "dataset_ids"?: [..] }`（`config_id` 必填）；Python 完成召回融合后流式生成答案：逐 token `event: answer_delta` → 终态 `event: answer_done`，**空命中**走 `event: recall_done`，失败 `event: error`。
>
> **session token claims**（HS256，**独立密钥** `RECALL_SESSION_JWT_SECRET`；与 Python 配置逐字一致）：`iss=tolink-java`、`aud=tolink-rag-frontend`、`scope=recall:stream`、`sub=<user_id 正整数字符串>`、`dataset_ids=<已校验的显式授权范围>`、`iat`、`exp`（默认 30s）。token **短期可复用**，不做 `jti`/一次性/防重放/撤销。
>
> **错误**：未登录 401；`datasetIds` 为空 400；`datasetIds` 越权 `RECALL_SCOPE_FORBIDDEN`（30002，403）；用户禁用 `AUTH_DISABLED`（20003，403）。Python 侧验签失败返回 `401 RECALL_SESSION_UNAUTHORIZED`、越权返回 `403 RECALL_SCOPE_FORBIDDEN`。
