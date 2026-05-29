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

## 链路摘要

Java 端保存原文件与 `document_parse_file` 聚合记录，触发解析时更新最新任务指针并发送扁平任务 MQ。Python 端执行解析，将终态与 Markdown 产物位置写入 `document_parsed_log` 并维护聚合表，再发送终态结果 MQ。Java 消费结果后只校验关联关系并推送前端 SSE 事件。

## 结果消费的接收兜底

`DocumentParseResultServiceImpl` 在归属校验通过后增加“当前任务过滤”：仅当消息 `task_id` 等于 `document_parse_file.latest_parse_task_id` 才推送终态 SSE，旧任务/乱序结果只记审计、不误推前端，指针缺失时 fail-open 放行。`DocumentParseStuckScanner` 定时扫描仍为 `created` 且超阈值的当前任务，重读 `document_parsed_log`：已终态则以 DB 为准复用 `publishResultEvent` 补推（兜底通知丢失），仍 `created` 则告警。Java 在所有分支均只读 DB，不回写 Python 负责的终态字段。错误处理与容器工厂细节见 `docs/architecture/mq_module.md`。
