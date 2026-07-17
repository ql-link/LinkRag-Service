# Java / Python RAG Integration

## Frontend APIs

- 首页最近文档：`GET /api/v1/files/recent?page=1&pageSize=5`，Java 按当前登录用户汇总所有数据集文档并分页返回。

## 职责边界

- Java：文件上传、元数据、权限、解析任务投递、结果查询。
- Python：文档解析、RAG 执行、LLM 调用、解析产物生成、解析终态持久化。

## 协作接口

- Java -> Python：`tolink.rag.parse_task`
- Java -> Python：`tolink.rag.document_delete`（删除通知，触发 Python 删衍生产物）
- Python 读取原文：`GET /api/v1/internal/files/{fileId}/content`

## 联调关注点

- MQ topic 名称和消息字段一致。
- PDF 解析任务的 `parse_task` 可携带 `pdf_parser_backend`，值来自数据集级 `pdf_config.pdf_parser_backend`；Java 仅透传明确配置，不补默认值，Python 未收到该字段时应继续按数据集配置、环境默认、系统默认兜底。
- Python 访问 Java 内部文件接口时携带约定鉴权信息。
- OSS object key 和数据库文件记录一致。
- 文档上传文件名由 Java 端做安全归一化：浏览器本地路径会被裁剪为 basename，常见业务符号（如括号、`#`、`&`、`+`、`=`）允许保留；控制字符、空名和超长名称会被拒绝。Python 端读取内部文件接口时应按数据库中的 `original_filename` 展示或记录，不要重新套更窄的文件名白名单。
- 解析终态结果由 Python 写入共享数据库，Java 通过查询接口读取，不再依赖终态回传 MQ。
- **链路追踪**：Java 端日志已接入 `trace_id`（HTTP 入口复用/新建 `X-Trace-Id` 头并回写响应头，异步线程透传 MDC）。Java -> Python 的 MQ 消息由 Java `MQSend` 适配层自动携带 `X-Trace-Id` header；Python -> Java 的 Kafka 消息由 Java 消费入口读取 `X-Trace-Id` / `x-trace-id` / `trace_id` / `trace-id` 并恢复 MDC。trace 只走传输 header，不写入 `parse_task` / `document_delete` / `chat_turn` / `usage_report` 业务 payload。
- **集中日志查询**：Java 管理端新增 `/api/v1/admin/logs` 查询代理，只访问 Loki 内网地址，不让前端直连 Loki。Java 服务名固定为 `tolink-service`；Python RAG 服务联调时必须把 `LOG_SERVICE_NAME` 设置为 `tolink-rag`，否则前端无法按 Java/Python 服务区分日志。
- **上传异步化**：上传接口立即返回 `uploadStatus=UPLOADING`，OSS 上传/终态回写在 Java 侧线程池异步完成；`parseImmediately=true` 的解析任务**只在 OSS 上传成功后**才投递（不会对尚未落 OSS 的文件触发解析）。Python 侧无需改动，仍以收到 `parse_task` 为准；前端需改为按 `uploadStatus` 轮询获取上传终态。
- **上传限制配置**：部署 `tolink.document-file.*` 提供默认值和硬上限；管理员可通过 Java 管理端 `PUT /api/v1/admin/document-file-config` 把完整覆盖值写入无 TTL Redis key。新上传在落库前读取当前有效值，Redis 故障时按实例最后有效快照/部署默认值降级。Python 解析端无需读取该 Redis key，仍只处理 Java 已接受并投递的文件。
- **多实例默认一致性**：所有 Java 实例必须使用相同的默认大小、硬上限和后缀全集；`runtime:document-file:default-fingerprint` 用于 readiness 检查。动态覆盖生效后不需要重启实例。
- **缓存补偿是 Java 内部链路**：Canal → Java CDC bridge → `tolink.cache.evict` → Java 缓存删除，不要求 Python 消费。当前只覆盖数据集解析配置、用户资料和公开博客发布索引；文档解析结果、模型配置和用量不缓存。
- **隐性删除 + 删除通知**：删除数据集/文件为软删保留原文件——Java 端不物理删 OSS 对象、不删解析表（`document_parse_file` / `document_parsed_log`）；这些衍生产物与 Python 侧 OSS 产物（清洗文件/向量）由 Python 负责删除。Java 在删除事务提交后（afterCommit）经 `tolink.rag.document_delete` **真实投递删除通知**（删数据集传 `dataset_id`、删文件传 `original_file_id`，`delete_type` 判别；尽力发、失败仅告警吞掉、无 DLQ；回滚不发）。**Python 侧需消费该通知并按范围删衍生产物（重复消息幂等、删不存在产物 no-op），本仓库未实现**。⚠️ **发布需两端协调**：该队列为点对点，Python 消费端就绪前 Java producer 不应单独上生产（否则消息无消费者会在 broker 积压）。

## 解析数据契约

- Java 写入原文件与 `document_parse_file.latest_parse_task_id`；Python 写入 `document_parsed_log`（Markdown 产物 + `retry_of_task_id`）与 `document_parse_pipeline`（端到端终态 `pipeline_status` + `superseded_by_task_id`）。
- `parse_task` 传递 `document_parse_file_id` 和 `trigger_mode`，便于 Python 创建日志记录；PDF 文件在数据集配置明确指定解析后端时额外带 `pdf_parser_backend`；重试再带 `is_retry`+`previous_task_id` 并复用上一轮 Markdown 坐标（`md_bucket`/`md_object_key`）做阶段恢复，已成功文件 Java 侧友好拒绝、不投递。
- `success` / `failed` 终态由 Python 写入 `document_parse_pipeline.pipeline_status`，前端通过 Java `parse-results` 查询接口轮询读取；Java 不再提供解析过程事件回调或 SSE 推送。
- Java 侧已下线 `tolink.rag.parse_result` 消费方；Python 停发该 topic 需与 Java 发布窗口协调。
