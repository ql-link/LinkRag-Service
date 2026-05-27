---
name: contract-guard
description: 检查 API、MySQL、Redis、MQ、OSS、错误码等跨模块或跨系统契约是否被影响。
when_to_use: "检查公共约定、契约影响、中间件影响、字段/消息/API 是否需要同步文档。"
---

# Contract Guard

## 必读

- `docs/reference/api_contracts.md`
- `docs/reference/mysql_schema.md`
- `docs/reference/mq_contracts.md`
- `docs/reference/error_codes.md`
- 相关 architecture/guides 文档
- 当前 technical_design 或代码 diff

## 检查

- 是否修改 API 路由、请求、响应、错误码。
- 是否修改 Entity、数据库脚本、共享字段语义。
- 是否修改 MQ topic、消息体、消费组、幂等字段。
- 是否新增 Redis key、TTL、缓存删除目标。
- 是否修改 OSS bucket/path/public-private 规则。
- 是否影响 Java/Python RAG 端协作。

## 输出

说明“未新增公共契约”或列出必须同步的文档。
