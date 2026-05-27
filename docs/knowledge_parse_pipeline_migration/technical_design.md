# Java 解析链路按 Python 数据契约迁移技术方案

## 数据模型

- `document_original_file` 删除旧解析字段，只保留上传元数据及上传失败原因。
- `document_parse_file` 一文件一行，保存 `latest_parse_task_id` 和 `parse_count`。
- `document_parsed_log` 一次解析一行，由 Python 写入状态、失败原因、解析产物位置及耗时。

## 消息与状态流

- Java 创建任务 ID，事务内更新聚合表指针并投递 `parse_task` 扁平消息。
- `parse_task` 包含 `document_parse_file_id`、源对象和目标 Markdown 对象定位字段。
- Python 落解析日志和终态后投递 `parse_result`；消息包含 `document_parsed_log_id`。
- Java 结果消费者只验证消息与数据库关联关系并向 SSE 服务发布事件。

## HTTP 接口

- 文件管理实现与契约文档保持 `/api/v1/datasets/{datasetId}/files`、`/api/v1/files/{fileId}` 路径。
- 新增解析提交、结果查询与 SSE 订阅入口。
- 内部文件下载由服务 Token 鉴权；进度回调仅接受 `processing` 与 `progress`。

## 验证

- 单测覆盖 MQ 序列化、任务指针与重复提交、终态消费只转发事件。
- 集成测试覆盖 schema、上传聚合记录、解析接口与内部事件鉴权。
- 提交前运行 Maven 测试与 AI/文档同步检查。
