# 管理端日志模块

本文档描述管理端集中日志查询代理、LogQL 构造和链路追踪过滤。

## 模块边界

代码入口：

| 职责 | 入口 |
| --- | --- |
| HTTP 入口 | `AdminLogController` |
| 查询编排 | `AdminLogQueryServiceImpl` |
| LogQL 构造 | `LokiLogQueryBuilder` |
| Loki 调用 | `LokiClient` |
| 响应解析 | `LokiLogParser` |
| trace 与日志写入 | `link-observability` |

接口：

```text
GET /api/v1/admin/logs
GET /api/v1/admin/logs/labels
```

两个接口都要求 `ADMIN` 角色。前端不直接访问 Loki，统一通过 Java 后端代理。

## 查询能力

日志查询支持：

| 参数 | 说明 |
| --- | --- |
| `service` | 服务名，只允许字母、数字、下划线、连字符 |
| `level` | `TRACE`、`DEBUG`、`INFO`、`WARN`、`ERROR`、`FATAL`、`ACCESS`、`AUDIT` |
| `trace_id` | 链路追踪 ID |
| `keyword` | 文本关键词，禁止控制字符 |
| `start_time` / `end_time` | ISO 时间 |
| `page` / `page_size` | 后端分页，受配置上限约束 |

`ACCESS` 和 `AUDIT` 不是普通 level label，而是按日志内容中的 `logger_name` 过滤。其他级别优先使用 Loki label 过滤。

## Label 列表

`GET /api/v1/admin/logs/labels` 从 Loki 查询 `service` label。若 Loki 不可用或返回为空，服务端返回兜底列表：

```text
tolink-service
tolink-rag
```

日志级别列表来自 `LokiLogQueryBuilder.SUPPORTED_LEVELS`。

## 安全约束

LogQL 由 `LokiLogQueryBuilder` 统一构造，不允许把用户输入直接拼接到查询语句。

主要护栏：

- service 使用白名单正则。
- level 必须在支持列表中。
- trace_id 复用 `TraceContext` 校验。
- keyword 禁止控制字符并限制长度。
- 时间支持 Instant、OffsetDateTime 和本地 LocalDateTime，解析失败返回 400。

## 与 observability 模块的关系

`link-observability` 负责 trace_id 上下文、HTTP Trace Filter、MDC 透传、访问日志和审计日志。管理端日志模块只负责查询，不负责生成日志。

排障链路通常是：

1. 从 API 响应、访问日志或审计日志拿到 trace_id。
2. 管理端日志接口按 service、level、trace_id 查询。
3. 根据同一 trace_id 串起 Java 和 Python 服务日志。

## 修改注意事项

- 新增日志级别时需同步 `SUPPORTED_LEVELS`、前端筛选项和本文档。
- 修改 Loki label 名称时需同步查询构造、部署配置和日志采集规则。
- 不要把 Loki 地址、查询凭据或内部 label 规则暴露给前端。
