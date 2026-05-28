# Knowledge File Module

## 职责

知识文件模块负责数据集文件上传、解析任务提交、结果查询和 SSE 事件转发。

## 主要入口

- Controller：`KnowledgeFileController`、`InternalKnowledgeFileController`
- Service：`KnowledgeFileService`、`KnowledgeParseTaskService`、`KnowledgeParseResultService`、`KnowledgeParseSseService`
- Entity：`KnowledgeOriginalFile`、`KnowledgeParseFile`、`KnowledgeParsedLog`
- MQ：`KnowledgeParseTaskMQ`、`KnowledgeParseResultMQ`

## 链路摘要

Java 端保存原文件与 `document_parse_file` 聚合记录，触发解析时更新最新任务指针并发送扁平任务 MQ。Python 端执行解析，将终态与 Markdown 产物位置写入 `document_parsed_log` 并维护聚合表，再发送终态结果 MQ。Java 消费结果后只校验关联关系并推送前端 SSE 事件。

## 结果消费的接收兜底

`KnowledgeParseResultServiceImpl` 在归属校验通过后增加“当前任务过滤”：仅当消息 `task_id` 等于 `document_parse_file.latest_parse_task_id` 才推送终态 SSE，旧任务/乱序结果只记审计、不误推前端，指针缺失时 fail-open 放行。`KnowledgeParseStuckScanner` 定时扫描仍为 `created` 且超阈值的当前任务，重读 `document_parsed_log`：已终态则以 DB 为准复用 `publishResultEvent` 补推（兜底通知丢失），仍 `created` 则告警。Java 在所有分支均只读 DB，不回写 Python 负责的终态字段。错误处理与容器工厂细节见 `docs/architecture/mq_module.md`。
