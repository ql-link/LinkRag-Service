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

## LLM

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/llm/providers` | 可用厂商与模型能力 |
| GET | `/api/v1/llm/configs` | 用户配置列表 |
| POST | `/api/v1/llm/configs` | 创建用户配置 |
| GET | `/api/v1/llm/configs/default` | 默认配置 |
| PATCH | `/api/v1/llm/configs/{id}/default` | 设置默认配置 |
| PATCH | `/api/v1/llm/configs/{id}` | 更新配置 |
| DELETE | `/api/v1/llm/configs/{id}` | 删除配置 |
| GET | `/api/v1/llm/usage/summary` | 用量汇总 |
| GET | `/api/v1/llm/usage/daily` | 日度用量 |
| GET | `/api/v1/llm/usage/logs` | 用量明细 |

> `configs` 相关响应（`UserLLMConfigDTO`）的能力字段为单数 `capability`（合法取值 `CHAT` / `EMBEDDING` / `OCR` / `VISION` / `REASONING` / `CODE` / `TOOL_CALLING` / `RERANK`，事实来源 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES`），曾误用复数 `capabilities`，前端需按 `capability` 取值。
>
> 用户侧 `GET /api/v1/llm/providers`（`ProviderController`）查询启用中的厂商与模型，供用户添加配置前选择，支持按 `capability` 过滤，返回 `ProviderModelDTO`；与管理端 `GET /api/v1/admin/providers`（分页管理视图）区分用途。
>
> `POST /api/v1/llm/configs` 按模型支持的全部能力展开为多条配置并返回列表；`GET /api/v1/llm/configs` 支持 `capability` 过滤；`GET /configs/default`、`PATCH /configs/{id}/default` 按 `capability` 维度维护默认配置。无效能力标识返回错误码 `10011`。

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
| POST | `/api/v1/recall/stream` | 用户态流式召回（SSE，`text/event-stream`） |

> 用户态召回网关：Java 校验 Sa-Token 登录态、用户状态（`status==1`）、`datasetIds` 归属后，签发内部 HS256 JWT 调用 Python `POST {RAG_PYTHON_BASE_URL}/api/v1/internal/recall/stream`（snake_case `query`/`user_id`/`dataset_ids`，header `Authorization: Bearer`、`X-Request-Id`），把结果裁剪为最小候选并以 SSE 转发。Java 不向 Python 透传前端 Sa-Token；内部 JWT `sub`/`dataset_ids` 与请求体 `user_id`/`dataset_ids` 自洽。
>
> **请求体**（camelCase）：`{ "query": "...", "datasetIds": [1,2] }`。`query` 非空；`datasetIds` 非 null，空列表表示「当前用户的全部数据集」（Java 展开为本人所有 dataset id，本人无库则直接返回空 `hits`、不调 Python）。首版拒绝 `docIds`/`topK`/`sources`/`strict`/`includeContent` 等未知字段（400）。
>
> **SSE 事件**：成功 `event: recall_done` + `data: {"hits":[{"chunkId","docId","datasetId"}]}`（保持 Python 顺序，首版不含正文与打分）；失败 `event: error` + `data: {"code","message"}`，`code` 为英文串码（`UNAUTHORIZED`/`RECALL_SCOPE_FORBIDDEN`/`RECALL_INVALID_REQUEST`/`RECALL_INTERNAL_AUTH_FAILED`/`RECALL_ALL_SOURCES_FAILED`/`RECALL_TIMEOUT`/`RECALL_UPSTREAM_ERROR`），不含内部堆栈。
>
> **建流前 / 建流后**：登录态、用户状态、参数、未知字段、`datasetIds` 越权、限流（默认每用户每分钟 10 次，固定窗口内允许突发）等校验在建流前完成，失败返回普通 HTTP 错误（401/403/400/429）；建流后（已开始 SSE）的任何错误（Python `error`、非 2xx、超时、未知）统一映射为 `event: error` 后关闭。前端断开时 Java 取消到 Python 的调用。
