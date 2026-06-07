# Java / Python RAG Integration

## Frontend APIs

- 首页最近文档：`GET /api/v1/files/recent?page=1&pageSize=5`，Java 按当前登录用户汇总所有数据集文档并分页返回。

## 职责边界

- Java：文件上传、元数据、权限、解析任务投递、结果查询、SSE 转发。
- Python：文档解析、RAG 执行、LLM 调用、解析产物生成、解析终态回传。

## 协作接口

- Java -> Python：`tolink.rag.parse_task`
- Java -> Python：`tolink.rag.document_delete`（删除通知，触发 Python 删衍生产物）
- Python -> Java：`tolink.rag.parse_result`
- Python 读取原文：`GET /api/v1/internal/files/{fileId}/content`
- Python 推送过程事件：`POST /api/v1/internal/parse-tasks/{taskId}/events`

## 联调关注点

- MQ topic 名称和消息字段一致。
- Python 访问 Java 内部文件接口时携带约定鉴权信息。
- OSS object key 和数据库文件记录一致。
- 解析终态结果可幂等处理。
- **链路追踪**：Java 端日志已接入 traceId（HTTP 入口复用/新建 `X-Trace-Id` 头，异步线程与 MQ 消费/定时任务各自串联）。当前 traceId **不随 MQ 消息或内部 HTTP 跨端传递**；若后续需 Java↔Python 全链路串联，可约定透传 `X-Trace-Id`（属增量协调项，暂未实现）。
- **上传异步化**：上传接口立即返回 `uploadStatus=UPLOADING`，OSS 上传/终态回写在 Java 侧线程池异步完成；`parseImmediately=true` 的解析任务**只在 OSS 上传成功后**才投递（不会对尚未落 OSS 的文件触发解析）。Python 侧无需改动，仍以收到 `parse_task` 为准；前端需改为按 `uploadStatus` 轮询获取上传终态。
- **隐性删除 + 删除通知**：删除数据集/文件为软删保留原文件——Java 端不物理删 OSS 对象、不删解析表（`document_parse_file` / `document_parsed_log`）；这些衍生产物与 Python 侧 OSS 产物（清洗文件/向量）由 Python 负责删除。Java 在删除事务提交后（afterCommit）经 `tolink.rag.document_delete` **真实投递删除通知**（删数据集传 `dataset_id`、删文件传 `original_file_id`，`delete_type` 判别；尽力发、失败仅告警吞掉、无 DLQ；回滚不发）。**Python 侧需消费该通知并按范围删衍生产物（重复消息幂等、删不存在产物 no-op），本仓库未实现**。⚠️ **发布需两端协调**：该队列为点对点，Python 消费端就绪前 Java producer 不应单独上生产（否则消息无消费者会在 broker 积压）。

## 解析数据契约

- Java 写入原文件与 `document_parse_file.latest_parse_task_id`；Python 写入 `document_parsed_log`（Markdown 产物 + `retry_of_task_id`）与 `document_parse_pipeline`（端到端终态 `pipeline_status` + `superseded_by_task_id`）。
- `parse_task` 传递 `document_parse_file_id` 和 `trigger_mode`，便于 Python 创建日志记录；重试再带 `is_retry`+`previous_task_id` 并复用上一轮 Markdown 坐标（`md_bucket`/`md_object_key`）做阶段恢复，已成功文件 Java 侧友好拒绝、不投递。
- `parse_result` 传递 `document_parsed_log_id`，Java 读取日志、流水线（`pipeline_status` 为终态权威源）及聚合记录校验后只转发 SSE；`document_parsed_log.task_status` 已删，Java 不再读取。
- `processing` / `progress` 经内部 HTTP 接口上报；`success` / `failed` 只经 MQ 回传。
- `parse_result` 若丢失或长时间未送达，Java 侧不依赖单条 MQ：前端结果查询按 `latest_parse_task_id` 读 DB 自愈，`DocumentParseStuckScanner` 也以 DB 为权威源对超阈值任务补推或告警。本兜底不要求 Python 配合改动消息体。
