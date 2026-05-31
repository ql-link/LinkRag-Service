# Document File Module

## 职责

文档文件模块负责数据集文件上传、解析任务提交、结果查询和 SSE 事件转发。

## 主要入口

- Controller：`DocumentFileController`、`InternalDocumentFileController`
- Service：`DocumentFileService`、`DocumentParseTaskService`、`DocumentParseResultService`、`DocumentParseSseService`
- 上传异步化：`DocumentUploadAsyncExecutor`（池线程编排 OSS 上传→回写→清理）、`DocumentUploadStatusWriter`（终态守卫回写）、`DocumentUploadTempStorage`（临时文件物化/清理/启动清理）、`DocumentUploadStuckScanner`（uploading 超时扫描）；专用线程池 `documentUploadExecutor`（见 `docs/guides/configuration.md`）
- Entity：`DocumentOriginalFile`、`DocumentParseFile`、`DocumentParsedLog`
- MQ：`DocumentParseTaskMQ`、`DocumentParseResultMQ`

## 上传异步化

上传接口（`POST /api/v1/datasets/{datasetId}/files`）同步阶段只做鉴权、数据集归属、格式/大小/文件名校验、同名处理、物化临时文件、落 `uploading` 记录并**立即返回 `uploadStatus=UPLOADING`**；OSS 上传、终态回写（`success`/`failed`）、`parseImmediately=true` 时的解析投递都移到 `documentUploadExecutor` 专用线程池，在事务提交后（afterCommit）异步执行。前端按 `uploadStatus` 轮询 list/detail 获取终态（**跨端依赖：前端需配合改为轮询**）。

- **失败可见性**：OSS 失败/池满拒绝/`uploading` 超时都落 `failed` + `failureReason`，由轮询发现，不以上传接口 HTTP 错误暴露。
- **同名重试**：受唯一约束 `uk_dataset_user_name_suffix` 约束，撞到的同名记录为 `failed` 则复用该行重置为 `uploading`（不插新行），为 `uploading`/`success` 则 400 拦截。
- **终态写入方**：请求线程只产出 `uploading`；`success`/`failed` 仅由异步任务或超时扫描经状态守卫更新写入。
- **持久性兜底**：`DocumentUploadStuckScanner` 定时把超时仍 `uploading` 的记录置 `failed`；启动时清理残留临时文件。
- **孤儿对象**：OSS 成功但 DB 回写失败时打告警日志（含 `objectKey`）留痕，记录仍 `uploading` 由超时扫描兜底，首版不做 OSS 对象补偿删除。

## 删除（隐性删除）

删除文档文件（`DELETE /api/v1/files/{fileId}`）或数据集（`DELETE /api/v1/datasets/{datasetId}`）采用隐性删除：

- **保留原文件**：`document_original_file`（及删数据集时的 `dataset`）软删（`@TableLogic is_deleted`），**不物理删 OSS 原文件对象**、不物理删 DB 行；原文件便于追溯 / 未来恢复。
- **同名重建/重传**：两表唯一键纳入判别列 `deleted_seq`（活=0 / 删=自身 id），软删死行退出“活名额”，删后可无限次重建 / 重传同名（详见 `docs/reference/mysql_schema.md`）。
- **会话/消息物理删**：删数据集级联物理删名下 `chat_conversation` + `chat_message`（衍生数据不保留；`chat_conversation` 已移除软删字段）。
- **解析域交 Python**：`document_parse_file` / `document_parsed_log` 与 Python 侧 OSS 产物的删除交 Python；Java 删除路径不再触碰 parse 两表（已移除原 `deleteParseRecords`）。
- **通知 Python（占位）**：删除事务提交后（afterCommit）预留发送点（载荷含被软删 `original_file_id`），本次仅留痕、不落 MQ producer / Python 侧（单独立项）。
- **读路径**：软删后列表 / 详情 / 同名校验经 `@TableLogic` 自动过滤；内部原文件接口对软删文件返回 404。

## 链路摘要

Java 端保存原文件与 `document_parse_file` 聚合记录，触发解析时先按 `document_parsed_log.parsed_object_key` + `document_parse_pipeline.pipeline_status` 分类首次/重试/已成功/运行中（已成功友好拒绝、不发 MQ；失败重试携带 `is_retry`+`previous_task_id` 并复用上一轮 Markdown 坐标做阶段恢复），更新最新任务指针并发送扁平任务 MQ。Python 端执行解析与后处理流水线（含稀疏向量），将 Markdown 产物写入 `document_parsed_log`、端到端终态写入 `document_parse_pipeline.pipeline_status`，再发送终态结果 MQ。Java 消费结果后只校验关联关系并推送前端 SSE 事件，并可沿 `retry_of_task_id` 回溯重试链（`DocumentParseRetryChainService`）。

## 结果消费的接收兜底

`DocumentParseResultServiceImpl` 在归属校验通过后增加“当前任务过滤”：仅当消息 `task_id` 等于 `document_parse_file.latest_parse_task_id` 才推送终态 SSE，旧任务/乱序结果只记审计、不误推前端，指针缺失时 fail-open 放行。`DocumentParseStuckScanner` 定时扫描 `document_parse_pipeline` 中仍运行中（`PENDING`/`PROCESSING`）且超阈值的当前任务，重读其 `pipeline_status`：已终态（`SUCCESS`/`FAILED`）则以 DB 为准复用 `publishResultEvent` 补推（兜底通知丢失），仍运行中则告警。Java 在所有分支均只读 DB，不回写 Python 负责的终态字段。错误处理与容器工厂细节见 `docs/architecture/mq_module.md`。
