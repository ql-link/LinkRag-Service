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
| GET | `/api/v1/admin/users/dashboard` | 用户统计看板，`days` 仅支持 7/30/90，默认 30 |
| PATCH | `/api/v1/admin/users/{id}/status` | 启用/禁用用户 |
| PATCH | `/api/v1/admin/users/{id}/role` | 修改用户角色 |
| GET | `/api/v1/admin/document-file-config` | 查询当前有效上传配置；优先 Redis 管理员覆盖，缺失时返回部署默认值 |
| PUT | `/api/v1/admin/document-file-config` | 完整替换全局上传大小和后缀限制；写入 Redis、无 TTL，跨实例即时生效 |
| GET | `/api/v1/admin/feedback` | 管理员反馈列表，支持 `page`、`pageSize`、`status`、`type` |
| GET | `/api/v1/admin/feedback/{id}` | 管理员反馈详情 |
| PATCH | `/api/v1/admin/feedback/{id}/status` | 更新反馈状态：`PENDING` / `PROCESSING` / `RESOLVED` / `CLOSED` |
| PATCH | `/api/v1/admin/feedback/{id}/priority` | 更新反馈优先级：`1` 高 / `2` 中 / `3` 低 |
| PATCH | `/api/v1/admin/feedback/{id}/reply` | 写入管理员回复，不自动修改反馈状态 |
| GET | `/api/v1/admin/logs` | 管理端集中日志查询代理，支持 `service`、`level`、`trace_id`、`keyword`、`start_time`、`end_time`、`page`、`page_size` |
| GET | `/api/v1/admin/logs/labels` | 管理端日志筛选标签，返回服务列表与固定日志级别列表 |
| GET | `/api/v1/admin/providers` | 管理端厂商列表（分页，按优先级倒序，返回 `iconUrl` / `iconObjectKey`） |
| POST | `/api/v1/admin/providers` | 创建系统厂商（`CreateProviderRequest`，含 `defaultProtocol` / `iconUrl` / `iconObjectKey`；直接创建为启用状态要求已有上架模型，否则返回 `10019`） |
| POST | `/api/v1/admin/providers/icon` | 上传厂商图标到公开 OSS，返回图标 URL 与 object key |
| PATCH | `/api/v1/admin/providers/{id}` | 部分更新厂商字段，支持更新/清空 `iconUrl` / `iconObjectKey`；更新为启用状态要求已有上架模型，否则返回 `10019` |
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
| GET | `/api/v1/admin/llm/configs` | 查询全部 SYSTEM 可执行配置，可按能力和启用状态过滤 |
| POST | `/api/v1/admin/llm/configs` | 原子创建 SYSTEM 配置，并可同时设为能力默认 |
| PUT | `/api/v1/admin/llm/configs/{configId}` | 原子更新同一配置 ID；`setAsDefault=true` 设为默认，`clearDefault=true` 清除当前配置持有的默认关系，两者均为 `false` 时保持不变 |
| PATCH | `/api/v1/admin/llm/configs/{configId}/active` | 标准启停；Dataset 或 SYSTEM 默认引用受保护 |
| POST | `/api/v1/admin/llm/configs/{configId}/emergency-disable` | 紧急停用；当前 SYSTEM 默认必须给出同能力替代 ID |
| DELETE | `/api/v1/admin/llm/configs/{configId}` | 删除无 Dataset/default 引用的 SYSTEM 配置 |
| PUT | `/api/v1/admin/llm/defaults/{capability}` | 切换 SYSTEM 能力默认，不改变旧配置状态或 Dataset 绑定 |

`POST /api/v1/user/avatar` 使用 `multipart/form-data`，字段名为 `file`。后端按 OSS `avatar` 业务规则校验：仅允许 `jpg` / `jpeg` / `png` / `gif` / `webp`，最大 5MB，写入公开 OSS（MinIO 部署时为 public bucket），object key 形如 `avatar/{userId}/{uuid}.{suffix}`。上传成功后将公开访问地址写入 `sys_user.avatar_url`，响应为更新后的 `UserProfileDTO`。

`PUT /api/v1/admin/document-file-config` 请求体为：

```json
{
  "maxSizeBytes": 10485760,
  "allowedSuffixes": ["pdf", "md"]
}
```

请求是完整替换，不支持部分更新；历史 `PATCH` 继续返回 405。`maxSizeBytes` 必须大于 0 且不超过部署硬上限 `tolink.document-file.hard-max-size-bytes`，后缀必须属于部署 `allowed-suffixes` 声明的全集。成功响应包含 `maxSizeBytes`、规范化后的 `allowedSuffixes`、`updatedBy`、`updatedAt`。Redis 写失败返回 `50003/503`，不得让单实例内存状态提前生效。

`GET /api/v1/admin/users/dashboard` 仅允许 ADMIN 访问。统计包含 USER 和 ADMIN，按 `Asia/Shanghai` 自然日计算；活跃用户为周期内至少有一次成功登录事件的去重用户，注册自动登录计入，失败登录不计入。响应包含 `rangeDays`、`totalUsers`、`breakdown{user,admin,enabled,disabled}`、`newUsers{current,previous,growthRate}`、`activeUsers{current,previous,growthRate}`、`trend[{date,newUsers,activeUsers}]`。上一等长周期为零时增长率为 `null`；无数据日期补零。`days` 非 7/30/90 返回 `20008/400`。

`POST /api/v1/admin/providers/icon` 使用 `multipart/form-data`，字段名为 `file`。后端按 OSS `providerIcon` 业务规则校验：仅允许 `jpg` / `jpeg` / `png` / `gif` / `webp`，最大 5MB，写入公开 OSS，object key 形如 `providerIcon/{uuid}.{suffix}`。上传成功后返回 `ProviderIconUploadDTO{ iconUrl, iconObjectKey }`；前端再将两者传给 `POST /api/v1/admin/providers` 或 `PATCH /api/v1/admin/providers/{id}`，分别写入 `llm_system_provider.icon_url` 与 `icon_object_key`。用户侧厂商目录和统一配置列表中的厂商图标都来自 `llm_system_provider`，前端不按 SYSTEM/USER 或 LinkRag 身份硬编码图标。

### Admin Logs

管理端日志接口仅允许 `ADMIN` 角色访问。Java 后端只代理查询 Loki，不向前端暴露 Loki 地址。

`GET /api/v1/admin/logs` 查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `service` | 否 | 服务名，如 `tolink-service` / `tolink-rag`；仅允许字母、数字、下划线、连字符，最长 64 |
| `level` | 否 | `TRACE` / `DEBUG` / `INFO` / `WARN` / `ERROR` / `FATAL` / `ACCESS` / `AUDIT` |
| `trace_id` | 否 | 链路追踪 ID，仅允许字母、数字、下划线、连字符，1~64 位 |
| `keyword` | 否 | 原始日志行文本过滤，最长 200，不允许控制字符 |
| `start_time` | 否 | ISO 时间；缺省按 `observability.loki.default-lookback` 回溯 |
| `end_time` | 否 | ISO 时间；缺省为当前时间 |
| `page` | 否 | 默认 1 |
| `page_size` | 否 | 默认 50，最大由 `observability.loki.max-page-size` 控制 |

响应为 `Result<PageResult<LogEntryDTO>>`，`items` 元素统一扁平输出：`time` / `level` / `service` / `host` / `pid` / `trace_id` / `logger_name` / `message` / `exception`。解析失败的非 JSON 日志不会丢弃，会把原始行填入 `message` 并返回 `raw`。由于 Loki `query_range` 不提供数据库式总数，`total` 表示本次查询从 Loki 取回后可分页的匹配条数，而不是全时间范围的精确总量。

`GET /api/v1/admin/logs/labels` 返回：

```json
{
  "services": ["tolink-service", "tolink-rag"],
  "levels": ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "ACCESS", "AUDIT"]
}
```

`services` 优先读取 Loki 的 `service` label；Loki 不可用或无数据时兜底返回 `tolink-service` / `tolink-rag`。

## LLM

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/llm/providers` | 可用厂商与模型能力（来源 `llm_provider_model`） |
| GET | `/api/v1/llm/configs` | 当前用户 USER 配置 + 每种能力当前 SYSTEM 默认；支持 `providerType/capability/isActive` 过滤 |
| POST | `/api/v1/llm/configs/setup-provider` | 按正式目录 upsert USER 运行快照；自然键相同复用 `configId` |
| PATCH | `/api/v1/llm/configs/{configId}/active` | 按全局 ID 标准启停 USER 配置 |
| POST | `/api/v1/llm/configs/{configId}/emergency-disable` | 所有者确认后紧急停用，保留 Dataset 绑定 |
| DELETE | `/api/v1/llm/configs/{configId}` | 按全局 ID 删除 USER 配置；Dataset 引用时拒绝 |
| GET | `/api/v1/llm/defaults` | 查询全部能力的用户覆盖、SYSTEM 默认和有效 `configId` |
| GET | `/api/v1/llm/defaults/{capability}` | 查询单能力默认关系；完全未配置时返回 409 |
| PUT | `/api/v1/llm/defaults/{capability}` | `{configId}` 设置当前用户同能力 USER 默认 |
| DELETE | `/api/v1/llm/defaults/{capability}` | 清除用户覆盖，恢复跟随 SYSTEM 默认 |
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
> `ExecutableLLMConfigDTO` 只以 `configId` 表示配置身份；`scope`（`SYSTEM` / `USER`）与 `editable` 仅用于展示和授权。响应不得再出现身份别名 `id`、`source`、`configSource` 或 `isSystemPreset`。默认选择使用独立的 `CapabilityDefaultDTO{capability,userDefaultConfigId,systemDefaultConfigId,effectiveConfigId}`，不混入配置 DTO。
>
> 精确配置校验固定顺序为：物理存在 → `is_active` → USER owner/SYSTEM 共享 → capability。配置不存在、停用、越权和能力不匹配分别返回 `10020/404`、`10021/409`、`10022/403`、`10023/400`，不回落默认配置或环境变量。
>
> 管理端创建/更新请求 `AdminPlatformConfigSaveRequest` 的事实来源二选一：`sourceProviderModelId` 复制正式目录，或 `catalogMutation` 在同一事务更新目录再生成运行快照。`setAsDefault=true` 时配置与 SYSTEM 默认在一个事务内写入；`clearDefault=true` 仅清除仍指向本次 `configId` 的 SYSTEM 默认关系；两者不能同时为 `true`，均为 `false` 时保持默认关系不变。默认写入或清除失败时整单回滚。已存在配置不能原地改变 capability，避免默认关系和数据集字段的能力语义失效；需要改能力时创建新配置。API Key 仅加密存储和脱敏输出，禁止进入日志。
>
> 用户 `setup-provider` 按 `(scope,owner_user_id,provider_id,model_name,capability)` upsert，刷新凭据复用原 `configId`，保留已有启用和默认状态。标准删除/停用保护 Dataset 引用；紧急停用保留绑定，使后续精确执行明确返回配置已停用。

### LLM 协议与入口契约

LLM 调用拆成两个正交维度：**`protocol`（API 家族或专用 adapter，决定鉴权与请求/响应怎么拼）× `capability`（用途，决定调哪个端点）**。Java 管理端负责把协议与**完整调用端点**落成数据下发，Python RAG 执行端按 `protocol` 选 adapter 后**直接打** `api_base_url`。三层语义（厂商默认模板 / 模型能力事实 / 用户配置快照）与 `protocol` 枚举（`openai` / `anthropic` / `google` / `jina` / `dashscope` / `bge_m3` / `doubao_vision`）见 `docs/api/mysql_schema.md`「协议与入口三层语义」。

**`api_base_url` 形态约定（2026-06 与 Python PR #192 对齐，语义已反转，两端必须严格一致）**：

- **模型能力层 / 统一可执行配置层** `api_base_url` 存**完整端点 URL**，Python 直打、**不再拼接任何后缀**。例：`https://api.openai.com/v1/chat/completions`、`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`。
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
| GET | `/api/v1/document-file-capabilities` | 查询文档、Markdown 图片与 ZIP 导入限制 |
| POST | `/api/v1/datasets/{datasetId}/files` | 上传文档文件（异步：立即返回 `uploadStatus=UPLOADING`） |
| GET | `/api/v1/datasets/{datasetId}/files` | 文件列表（支持按 `uploadStatus` 过滤，前端据此轮询上传终态） |
| GET | `/api/v1/files/recent` | 当前用户全局最近文档列表 |
| GET | `/api/v1/files/{fileId}` | 文件详情 |
| DELETE | `/api/v1/files/{fileId}` | 删除文件 |
| POST | `/api/v1/files/{fileId}/parse` | 提交解析 |
| GET | `/api/v1/datasets/{datasetId}/files/parse-results` | 解析结果列表 |

> 文档上传异步化：`POST .../files` 在同步校验（鉴权/数据集归属/格式/大小/文件名/同名）通过后立即返回 `uploadStatus=UPLOADING`；OSS 上传与终态回写（`UPLOAD_SUCCESS`/`UPLOAD_FAILED`）在后台线程池异步完成。同步校验失败仍即时返回 4xx（未登录/无权 401-404、格式/大小/文件名/同名 400）。前端需按 `uploadStatus` 轮询 list/detail 获取终态。同名重试：撞到 `UPLOAD_FAILED` 同名文件会复用原记录重传，撞到 `UPLOADING`/`UPLOAD_SUCCESS` 返回 400。

> Markdown 本地图片资源包：上传仍为 `multipart/form-data`。普通文件只传 `file`、`parseImmediately`；Markdown 资源包另传 `matchMode`（`FULL_PATH` 或 `SHALLOW_BASENAME`）、`documentPath`、同序的 `assets[]` / `assetRelativePaths[]` 与 `assetInventoryPaths[]`。ZIP 只在浏览器受限解压，Java 不接收原始 ZIP；文件夹使用完整相对路径，单文件补图只使用所选文件夹直接子级 basename。Java 识别标准 Markdown（含引用式）、HTML `<img>` 与 Obsidian 图片语法，按大小写敏感、NFC 和有限 percent-decode 候选重算；命中引用改写为 `tolink-raw://raw/...`。`DocumentFileDTO`、详情和解析结果可返回 `assetSummary`。缺失、歧义或不支持图片属于软问题：文件仍上传成功但不自动解析；`POST .../parse` 默认返回 `30020/409` 和 `data.assetSummary`，用户确认后以 `ignoreMissingAssets=true` 继续。并发重复提交解析会返回同一 `taskId`，`alreadyRunning=true`，不重复发 MQ。v1 manifest 不可读取时返回 `50004/503` 并停止解析。

> `GET /api/v1/document-file-capabilities` 返回 `featureEnabled`、动态文档后缀/大小、图片扩展名/MIME/数量/字节/路径限制、ZIP 压缩大小/条目/展开大小/压缩比/深度限制，以及合法 `matchModes`。Web 必须在导入前读取该接口，但其预检不是信任边界，Java 仍会权威复核。

> 创建数据集：`POST /api/v1/datasets` 请求体除 `name`/`description` 外，必须提供 `sparse_embedding_config_id` 与 `dense_embedding_config_id`。两个字段均直接引用全局 `llm_model_config.id`，不再携带 `source` 或其它身份别名；能力必须分别为 `SPARSE_EMBEDDING` / `EMBEDDING`。Java 内部按“配置不存在 → 已停用 → 越权 → 能力不匹配”的固定顺序校验，Dataset 写接口统一包装为 `INVALID_DATASET_MODEL_BINDING(10028)`，并在 `data.field` 返回失败字段。创建成功时同步写入 `dataset_parse_config` 默认行并固化这两个绑定，后续解析构建向量与召回均以数据集绑定为准，不跟随用户默认选择漂移。

> 解析/检索配置（`/parse-config`）：请求/响应为 `{sparse_embedding_config_id, dense_embedding_config_id, enhancement_chat_config_id, enhancement_vision_config_id, rerank_config_id, chunking, enhancement, pdf, recall}`，字段名 snake_case，与 Python `dataset_config` Pydantic 模型对齐。稀疏/稠密绑定创建后不可修改；CHAT、VISION、RERANK 绑定可以在保存时替换或清空。启用表格增强或标题层级时必须绑定 `CHAT`，启用图片增强时必须绑定 `VISION`，`recall.enable_rerank=true` 时必须绑定 `RERANK`；即使对应功能暂未启用，只要提交了可选 ID，Java 仍按其精确能力做预检。PUT 全量覆盖四类 JSON 配置，旧 JSON 缺失字段按模型默认值兼容读取。内部精确模型校验沿用 `10020` → `10021` → `10022` → `10023` 的固定优先级，Dataset 写接口统一返回 `10028` 与 `data.field`；Python 在实际 parse/recall 前发现当前功能必需绑定缺失时返回 `10029` 与完整 `missing_bindings`。其余分块、PDF、召回范围校验与 Python Pydantic 模型保持一致。

## OSS / Internal

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/oss-files/{bizType}` | 通用 OSS 上传 |
| GET | `/api/v1/oss-files/public/**` | local OSS 模式下的公开文件预览路由 |
| GET | `/api/v1/internal/files/{fileId}/content` | Python 端读取私有文件内容 |

## Feedback

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/feedback` | 匿名提交反馈，`multipart/form-data`，可选 `file` 附件 |

`POST /api/v1/feedback` 接收 `type`（可选，默认 `OTHER`）、`title`（必填，最长 128）、`content`（必填，最长 5000）和可选 `file`。附件上传到公开桶 `tolink-public`，数据库只保存 `attachmentObjectKey`（object key 形如 `feedback/yyyy/MM/{uuid}.{suffix}`，精度到月）；响应 `FeedbackDTO` 同时返回 `attachmentObjectKey` 与可直接访问的 `attachmentUrl`（由后端按公开桶 endpoint + bucket + key 拼装，无附件时为空）。管理端 `/api/v1/admin/feedback` 列表与详情同样返回 `attachmentUrl`。

## Blog

管理端接口全部要求 `ADMIN` 角色；公开端无需登录。管理列表和公开列表均不返回 Markdown 正文，管理详情和公开详情才按 `blog_post.content_object_key` 从 PUBLIC OSS 对象读取正文。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/blog/posts` | 管理端文章列表，可按 `status` 过滤，不含正文 |
| GET | `/api/v1/admin/blog/posts/{postId}` | 管理端文章详情，含 Markdown 正文 |
| POST | `/api/v1/admin/blog/posts` | 创建草稿 |
| PATCH | `/api/v1/admin/blog/posts/{postId}` | 更新标题、摘要或封面资源；`slug` 不允许手动更新 |
| POST | `/api/v1/admin/blog/posts/{postId}/content/import` | 导入 `.md` / `.markdown` 草稿；正文图片自动入 PUBLIC OSS 并改写 Markdown 引用 |
| POST | `/api/v1/admin/blog/posts/{postId}/content` | 导入 `.md` / `.markdown` 草稿的兼容旧路径 |
| PUT | `/api/v1/admin/blog/posts/{postId}/content` | 保存编辑器当前完整 Markdown 正文；支持自动保存 |
| POST | `/api/v1/admin/blog/posts/{postId}/publish` | 发布文章 |
| POST | `/api/v1/admin/blog/posts/{postId}/unpublish` | 下架文章 |
| DELETE | `/api/v1/admin/blog/posts/{postId}` | 软删文章；事务提交后 best-effort 清理当前正文和资源对象 |
| GET | `/api/v1/admin/blog/posts/{postId}/assets` | 查询文章未删除图片资源，支持 `assetType` 筛选 |
| POST | `/api/v1/admin/blog/posts/{postId}/assets` | 上传 `COVER` 封面图片或 `CONTENT_IMAGE` 正文图片；正文图片返回 `markdownText` |
| DELETE | `/api/v1/admin/blog/posts/{postId}/assets/{assetId}` | 删除资源；正文图片仍被当前 Markdown 引用时拒绝 |
| GET | `/api/v1/blog/posts` | 公开文章列表，只返回已发布文章，不含正文 |
| GET | `/api/v1/blog/posts/{slug}` | 公开文章详情，含 Markdown 正文 |

创建草稿时后端生成去掉连字符的 32 位小写 UUID 作为 `slug`，前端不提交也不更新 `slug`。不提供 `/api/v1/admin/blog/posts/{postId}/content/download` 下载路由。正文使用 PUBLIC OSS UUID object key，替换正文时先上传新对象，再切换 `blog_post.content_object_key`。Markdown 正文中的图片引用由后端自动处理：可成功下载的 `http` / `https` 图片会下载后写入 PUBLIC OSS 并记录为 `blog_asset.CONTENT_IMAGE`，`data:image/*;base64` 图片会解码后写入 PUBLIC OSS 并记录资源，随后 Markdown 中的图片地址替换为公开 URL；已属于当前文章 `blog_asset` 的图片允许继续使用完整公开 URL 或 `/{PUBLIC bucket}/{objectKey}` 形式（如 `/tolink-public/blog/{postId}/images/{uuid}.png`），不会被重复抓取或按相对路径拒绝；网络图片下载失败、超时、大小超限、类型不允许或安全校验失败时保留原 URL，不阻断导入/保存；其它本地相对路径图片会返回 400。

公开列表只缓存固定前 100 条轻量发布索引；超过覆盖范围的页直接查询 MySQL，不按任意 `page/pageSize` 组合创建 Redis key。公开详情的 Markdown 正文不进入 Redis，响应使用 HTTP 条件缓存：

- 首次成功响应：`ETag: W/"..."`、`Cache-Control: public, no-cache`，不发送 `Last-Modified`。
- 客户端携带匹配的 `If-None-Match` 时，后端在读取 OSS 正文前返回 304 和空响应体。
- ETag 由当前公开元数据和正文 object key 等公开表示字段生成；同秒修改标题、摘要、封面或切换到新 UUID 正文 key 都会变化。
- 不存在、下架、软删或正文读取失败返回非 304，并设置 `Cache-Control: no-store`。
- 前端无需使用 `localStorage`；浏览器按标准 `ETag` 重新验证即可。

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
