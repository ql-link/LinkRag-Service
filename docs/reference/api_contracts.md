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
| POST | `/api/v1/datasets/{datasetId}/files` | 上传文档文件 |
| GET | `/api/v1/datasets/{datasetId}/files` | 文件列表 |
| GET | `/api/v1/files/{fileId}` | 文件详情 |
| DELETE | `/api/v1/files/{fileId}` | 删除文件 |
| POST | `/api/v1/files/{fileId}/parse` | 提交解析 |
| GET | `/api/v1/datasets/{datasetId}/files/parse-events` | SSE 解析事件 |
| GET | `/api/v1/datasets/{datasetId}/files/parse-results` | 解析结果列表 |

## OSS / Internal

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/oss-files/{bizType}` | 通用 OSS 上传 |
| GET | `/api/v1/internal/files/{fileId}/content` | Python 端读取私有文件内容 |
| POST | `/api/v1/internal/parse-tasks/{taskId}/events` | Python 端推送解析过程事件 |

统一响应模型为 `Result<T>`，分页模型为 `PageResult<T>`。

解析过程接口只接受 `processing` / `progress`；终态结果通过 `tolink.rag.parse_result` MQ 推送。解析结果查询读取 `document_parse_file.latest_parse_task_id` 所指向的日志状态。
