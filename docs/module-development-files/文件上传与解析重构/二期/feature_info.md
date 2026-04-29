# 功能信息卡

## 1. 基础信息

- 模块名称：文件解析 MQ 投递与解析日志重构
- 当前期次：二期
- 业务域：storage
- 当前状态：代码实现完成，待测试交付
- 复杂度等级：L3
- 当前分支：refactor/update-file-upload-parse

## 2. 功能摘要

- 背景：一期完成原文件和解析文件记录初始化后，二期需要重构解析触发、MQ 投递和 Python 解析日志创建职责。
- 目标：用户触发解析时，Java 端只负责生成任务 ID、更新解析文件记录并发送 MQ；Python 端消费 MQ 后创建解析日志、执行解析并维护结果。
- 本期目标：
  - MQ 消息中携带 `task_id`、`original_file_id`、`parsed_file_id`、用户和数据集归属、源文件对象定位、目标 Markdown 对象定位。
  - 废除旧 `document_parse_task`，改为由 Python 创建和维护 `document_parse_log`。
  - 重新解析时复用同一条 `document_parsed_file`，新增一条 `document_parse_log`。
  - MQ 同步投递失败时直接向前端返回失败，重新点击解析后生成新的 `task_id` 再次投递。
  - 同一原文件解析进行中时，不允许再次投递解析。
  - 技术方案已确认采用“先更新数据库、再同步发送 MQ、失败回滚事务”的主链路。
- 本期不做：
  - 不重新设计原文件上传链路。
  - 不做向量化、检索、问答消费链路。
  - 不引入 Outbox 作为默认可靠投递方案。

## 3. 影响范围

- 关联模块：
  - Java 解析触发服务
  - Python MQ 消费与解析 Pipeline
  - 前端解析状态展示
- 关联中间件：
  - MySQL
  - MinIO
  - Kafka / RabbitMQ
- 关联数据库：
  - `document_parsed_file`
  - `document_parse_log`
  - `document_original_file`

## 4. 文档清单

- `feature_info.md`
- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- 后续待补：`testing_delivery.md`

## 5. 关联功能

- 上游功能：
  - 一期文件上传表结构与业务流程重构
- 下游功能：
  - 解析结果展示
  - 解析失败重试
  - 后续向量化和检索链路

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `requirement.md`
3. `technical_design.md`
4. `../一期/requirement.md`
5. `docs/db/init.sql`
6. `AGENTS.md`
7. `project_info.md`
8. `docs/architecture/middleware_contract.md`
