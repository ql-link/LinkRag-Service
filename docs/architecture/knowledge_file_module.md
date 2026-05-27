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
