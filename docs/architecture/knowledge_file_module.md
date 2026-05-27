# Knowledge File Module

## 职责

知识文件模块负责数据集文件上传、解析任务提交、结果查询和 SSE 事件转发。

## 主要入口

- Controller：`KnowledgeFileController`、`InternalKnowledgeFileController`
- Service：`KnowledgeFileService`、`KnowledgeParseTaskService`、`KnowledgeParseResultService`、`KnowledgeParseSseService`
- Entity：`KnowledgeOriginalFile`、`KnowledgeParsedFile`、`KnowledgeParseTask`
- MQ：`KnowledgeParseTaskMQ`、`KnowledgeParseResultMQ`

## 链路摘要

Java 端保存文件和元数据后发送解析任务 MQ。Python 端执行解析并回传结果。Java 消费结果后更新状态并推送前端事件。
