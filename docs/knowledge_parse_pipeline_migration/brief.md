# Java 解析链路按 Python 数据契约迁移

## 背景

当前 Java 实现把解析状态和产物位置写入 `document_original_file`，结果 MQ 仍使用旧 envelope 格式且结果 Topic 常量配置错误。Python 端已固定新的解析表结构和扁平 MQ 契约，需要 Java 端完成对齐。

## 目标

- 原文件表只保存上传事实。
- Java 通过 `document_parse_file` 维护文件级最新任务指针并发送解析任务。
- Python 创建和推进 `document_parsed_log`、更新解析产物与累计次数。
- Java 消费终态结果后只校验归属并通过 SSE 通知前端。

## 不做

- 不回填旧解析数据或兼容旧 envelope MQ。
- 不引入 Flyway/Liquibase 或生产增量迁移脚本。
- 不接入解析后处理流水线和 Chunk 存储。

## 关键契约

- `parse_task` Topic：`tolink.rag.parse_task`，聚合记录字段为 `document_parse_file_id`。
- `parse_result` Topic：`tolink.rag.parse_result`，日志记录字段为 `document_parsed_log_id`。
- 内部 HTTP 回调只传递 `processing` / `progress` 运行期事件，终态由 MQ 回传。

## 风险

- 现有开发数据库需要按新 schema 重建，旧解析状态不保留。
- Java 与 Python 必须同时使用新的表名和消息字段，否则任务或结果无法关联。
