# 功能信息卡

## 1. 基础信息

- 模块名称：文件上传与解析重构
- 当前期次：三期
- 业务域：storage
- 当前状态：待最终审核
- 复杂度等级：L3
- 当前分支：refactor/update-file-upload-parse

## 2. 功能摘要

- 背景：当前重构一期已完成上传链路和解析文件聚合记录初始化，二期已完成解析提交、同步 MQ 投递失败回滚、Python 回调 + SSE 单通道和数据库终态兜底。
- 目标：把 Python -> Java 的解析终态通知从内部 HTTP 回调切换为 `parse_result` MQ，同时保留进度事件 HTTP 回调链路。
- 本期定位：
- 不重复一期/二期已实现的上传与解析提交流程。
- 只承接“解析终态 MQ 回传替换回调终态”的主链路调整。
- `processing/progress` 继续通过内部 HTTP 回调上报。

## 3. 本期目标与边界

### 3.1 本期纳入三期的目标

| 目标 | 描述 | 备注 |
| :--- | :--- | :--- |
| `parse_result` 终态回传 MQ | Python 在解析完成后向 `tolink.rag.parse_result` 发送终态结果消息，Java 消费后转发前端 | 只承接 `success/failed` |
| HTTP 回调职责收缩 | `processing/progress` 继续走内部回调，终态不再通过回调通知 | 避免两条终态主链路并存 |
| 旧兼容废案替换 | 旧 `parse_result` envelope + payload 废案不再作为新协议基线 | 新协议采用扁平 JSON |

### 3.2 本期不纳入三期、迁入四期的目标

| 目标 | 当前状态 | 去向 |
| :--- | :--- | :--- |
| SSE 多实例跨节点进度分发 | 二期仍为单机内存 `SseEmitter` | 四期 |
| 原文件软删除与解析产物删除 | 删除语义仍偏基础链路 | 四期 |
| 解析产物版本管理 | 仍只有最新任务/最新结果口径 | 四期 |
| MQ 投递可靠性增强（Outbox） | 二期未引入本地消息表或 Outbox | 四期 |
| 解析日志查询与归档治理 | 只满足主链路闭环，未补历史查询治理 | 四期 |
| 解析后下游知识处理衔接 | 当前仍止步于解析完成 | 四期 |

### 3.3 本期消息契约关键口径

- `parse_result` topic：`tolink.rag.parse_result`
- Kafka 消费组：`tolink-document-prase`
- 消息体使用扁平 JSON，不沿用历史 envelope + payload 结构
- 字段名采用 `document_parse_log_id`，不再使用 `document_parse_task_id`
- `parse_finished_at` 必须带时区，采用 ISO 8601 时间字符串
- `task_status` 只允许 `success` / `failed`

## 4. 文档清单

- `feature_info.md`
- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`

## 5. 关联期次

- 一期：上传链路与解析聚合记录初始化
- 二期：解析提交、`parse_task` MQ、Python 回调 + SSE 主链路
- 三期：终态 `parse_result` MQ 回传替换终态回调
- 四期：承接多实例广播、删除语义、版本管理、可靠投递、日志治理、下游衔接

## 6. 关联文档

- `../一期/feature_info.md`
- `../二期/feature_info.md`
- `../二期/requirement.md`
- `../二期/technical_design.md`
- `../四期/feature_info.md`
- `docs/组件和数据库约定/middleware_contract.md`

## 7. 推荐阅读顺序

1. `feature_info.md`
2. `../二期/feature_info.md`
3. `requirement.md`
4. `technical_design.md`
5. `../二期/requirement.md`
6. `../二期/technical_design.md`
7. `../四期/feature_info.md`
8. `AGENTS.md`
9. `project_info.md`
10. `docs/组件和数据库约定/middleware_contract.md`
