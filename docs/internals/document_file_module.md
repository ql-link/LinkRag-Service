# Document File Module

## 查询能力

- 数据集内文件列表：`GET /api/v1/datasets/{datasetId}/files`，按当前用户和数据集过滤，支持上传状态筛选。
- 全局最近文档：`GET /api/v1/files/recent`，按当前用户过滤所有数据集下的原始文档，使用 `created_at DESC, id DESC` 稳定排序并返回 `PageResult<DocumentFileDTO>`。

## 职责

文档文件模块负责数据集文件上传、解析任务提交和结果查询。

## 主要入口

- Controller：`DocumentFileController`、`InternalDocumentFileController`
- Service：`DocumentFileService`、`DocumentParseTaskService`
- 上传异步化：`DocumentUploadAsyncExecutor`（池线程编排 OSS 上传→回写→清理）、`DocumentUploadStatusWriter`（终态守卫回写）、`DocumentUploadTempStorage`（临时文件物化/清理/启动清理）、`DocumentUploadStuckScanner`（uploading 超时扫描）；专用线程池 `documentUploadExecutor`（见 `docs/ops/configuration.md`）
- Entity：`DocumentOriginalFile`、`DocumentParseFile`、`DocumentParsedLog`
- MQ：`DocumentParseTaskMQ`

## 上传异步化

上传接口（`POST /api/v1/datasets/{datasetId}/files`）同步阶段只做鉴权、数据集归属、格式/大小/文件名校验、同名处理、物化临时文件、落 `uploading` 记录并**立即返回 `uploadStatus=UPLOADING`**；OSS 上传、终态回写（`success`/`failed`）、`parseImmediately=true` 时的解析投递都移到 `documentUploadExecutor` 专用线程池，在事务提交后（afterCommit）异步执行。前端按 `uploadStatus` 轮询 list/detail 获取终态（**跨端依赖：前端需配合改为轮询**）。

- **失败可见性**：OSS 失败/池满拒绝/`uploading` 超时都落 `failed` + `failureReason`，由轮询发现，不以上传接口 HTTP 错误暴露。
- **同名重试**：受唯一约束 `uk_dataset_user_name_suffix` 约束，撞到的同名记录为 `failed` 则复用该行重置为 `uploading`（不插新行），为 `uploading`/`success` 则 400 拦截。
- **文件名校验**：上传前会裁剪浏览器可能带上的本地路径，只保留 basename；文件名不再使用严格字符白名单，允许括号、`#`、`&`、`+`、`=` 等常见业务命名字符，但仍拒绝空文件名、控制字符、`.`/`..` 和超过 `document_original_file.original_filename` 长度上限的名称。后缀校验基于归一化后的文件名执行。
- **终态写入方**：请求线程只产出 `uploading`；`success`/`failed` 仅由异步任务或超时扫描经状态守卫更新写入。
- **持久性兜底**：`DocumentUploadStuckScanner` 定时把超时仍 `uploading` 的记录置 `failed`；启动时清理残留临时文件。
- **孤儿对象**：OSS 成功但 DB 回写失败时打告警日志（含 `objectKey`）留痕，记录仍 `uploading` 由超时扫描兜底，首版不做 OSS 对象补偿删除。

## 删除（隐性删除）

删除文档文件（`DELETE /api/v1/files/{fileId}`）或数据集（`DELETE /api/v1/datasets/{datasetId}`）采用隐性删除：

- **保留原文件**：`document_original_file`（及删数据集时的 `dataset`）软删（`@TableLogic is_deleted`），**不物理删 OSS 原文件对象**、不物理删 DB 行；原文件便于追溯 / 未来恢复。
- **同名重建/重传**：两表唯一键纳入判别列 `deleted_seq`（活=0 / 删=自身 id），软删死行退出“活名额”，删后可无限次重建 / 重传同名（详见 `docs/api/mysql_schema.md`）。
- **会话/消息物理删**：删数据集级联物理删名下 `chat_conversation` + `chat_message`（衍生数据不保留；`chat_conversation` 已移除软删字段）。
- **解析域交 Python**：`document_parse_file` / `document_parsed_log` 与 Python 侧 OSS 产物的删除交 Python；Java 删除路径不再触碰 parse 两表（已移除原 `deleteParseRecords`）。
- **通知 Python（已落地）**：删除事务提交后（afterCommit）经 `tolink.rag.document_delete` 投递删除通知（删数据集传 `dataset_id`、删文件传 `original_file_id`，`delete_type` 判别）；生产侧 `DocumentDeleteNotifier` 尽力发——发送失败/发送器缺失仅告警吞掉、不影响已提交的删除、无 DLQ（字段与约定见 `docs/reference/mq_contracts.md`）。Python 侧消费删衍生产物在另一仓库，发布需两端协调（点对点队列，消费端就绪前 producer 不单独上生产）。
- **读路径**：软删后列表 / 详情 / 同名校验经 `@TableLogic` 自动过滤；内部原文件接口对软删文件返回 404。

## 链路摘要

Java 端保存原文件与 `document_parse_file` 聚合记录，触发解析时先按 `document_parsed_log.parsed_object_key` + `document_parse_pipeline.pipeline_status` 分类首次/重试/已成功/运行中（已成功友好拒绝、不发 MQ；失败重试携带 `is_retry`+`previous_task_id` 并复用上一轮 Markdown 坐标做阶段恢复），更新最新任务指针并发送扁平任务 MQ。Python 端执行解析与后处理流水线（含稀疏向量），将 Markdown 产物写入 `document_parsed_log`、端到端终态写入 `document_parse_pipeline.pipeline_status`。Java 不再消费 `tolink.rag.parse_result`，前端通过 `parse-results` 查询接口读取终态，并可沿 `retry_of_task_id` 回溯重试链（`DocumentParseRetryChainService`）。

## 解析终态查询

解析终态以 Python 写入的共享数据库为准。`GET /api/v1/datasets/{datasetId}/files/parse-results` 通过 `document_parse_file.latest_parse_task_id` 定位当前任务，读取对应 `document_parsed_log` 与 `document_parse_pipeline.pipeline_status`，Java 只读不回写 Python 负责的终态字段。
