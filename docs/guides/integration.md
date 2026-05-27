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
