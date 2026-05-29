# Java / Python RAG Integration

## 职责边界

- Java：文件上传、元数据、权限、解析任务投递、结果查询、SSE 转发。
- Python：文档解析、RAG 执行、LLM 调用、解析产物生成、解析终态回传。

## 协作接口

- Java -> Python：`tolink.rag.parse_task`
- Python -> Java：`tolink.rag.parse_result`
- Python 读取原文：`GET /api/v1/internal/files/{fileId}/content`
- Python 推送过程事件：`POST /api/v1/internal/parse-tasks/{taskId}/events`

## 联调关注点

- MQ topic 名称和消息字段一致。
- Python 访问 Java 内部文件接口时携带约定鉴权信息。
- OSS object key 和数据库文件记录一致。
- 解析终态结果可幂等处理。
- **上传异步化**：上传接口立即返回 `uploadStatus=UPLOADING`，OSS 上传/终态回写在 Java 侧线程池异步完成；`parseImmediately=true` 的解析任务**只在 OSS 上传成功后**才投递（不会对尚未落 OSS 的文件触发解析）。Python 侧无需改动，仍以收到 `parse_task` 为准；前端需改为按 `uploadStatus` 轮询获取上传终态。
- **隐性删除**：删除数据集/文件为软删保留原文件——Java 端不物理删 OSS 对象、不删解析表（`document_parse_file` / `document_parsed_log`）；这些衍生产物与 Python 侧 OSS 产物（清洗文件/向量）由 Python 负责删除，Java 在删除事务提交后（afterCommit）预留通知发送点（MQ 占位、未实现，单独立项）。Python 侧本次无需改动。

## 解析数据契约

- Java 写入原文件与 `document_parse_file.latest_parse_task_id`；Python 写入 `document_parsed_log` 和解析成功次数。
- `parse_task` 传递 `document_parse_file_id` 和 `trigger_mode`，便于 Python 创建日志记录。
- `parse_result` 传递 `document_parsed_log_id`，Java 读取日志及聚合记录校验后只转发 SSE。
- `processing` / `progress` 经内部 HTTP 接口上报；`success` / `failed` 只经 MQ 回传。
- `parse_result` 若丢失或长时间未送达，Java 侧不依赖单条 MQ：前端结果查询按 `latest_parse_task_id` 读 DB 自愈，`DocumentParseStuckScanner` 也以 DB 为权威源对超阈值任务补推或告警。本兜底不要求 Python 配合改动消息体。
