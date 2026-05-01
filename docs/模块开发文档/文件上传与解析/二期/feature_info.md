# 功能信息卡

## 1. 基础信息

- 模块名称：文件上传与解析协同重构
- 当前期次：二期
- 业务域：storage
- 当前状态：待最终审核
- 复杂度等级：L3
- 当前分支：feature/knowledge_file_upload_and_parse

## 2. 功能摘要

- 背景：一期已完成原文件上传、MinIO 存储、原文件记录、上传幂等、失败重试和超时处理；二期补齐上传成功后的解析协同链路。
- 目标：用户可对已上传成功的原文件发起自动解析或手动解析，Java 端创建解析任务并投递 MQ，Python 端消费任务后执行解析、上报进度和结果，并维护解析任务与最新解析产物。
- 本期目标：
  - 建立解析任务表，记录同一原文件的多次解析历史。
  - 建立解析文件表，记录每个原文件当前最新成功解析结果和成功解析次数。
  - 定义 Java 创建解析任务、投递 MQ、投递失败处理和用户重试边界。
  - 定义 Python 解析进度上报与前端进度条展示边界。
  - 定义解析失败后的可见状态与再次解析入口。
- 本次不做：
  - 不实现向量化、检索、问答消费链路。
  - 不实现解析结果历史版本人工切换。
  - 不引入 Redis 作为解析进度默认依赖。
  - 不修改 MQ、OSS framework 抽象。

## 3. 影响范围

- 关联模块：
  - `link-api`
  - `link-service`
  - `link-model`
  - `link-mapper`
- 关联中间件：
  - MySQL
  - OSS / MinIO
  - MQ
- 关联外部系统：
  - Python 解析服务

## 4. 文档清单

- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`

## 5. 关联功能

- 上游期次：
  - 一期原文件上传链路
- 依赖的上游能力：
  - 原文件表
  - 原文件 MinIO 对象定位
  - 数据集权限边界
  - 登录与鉴权
- 受影响的下游功能：
  - 文件解析结果展示
  - 后续知识处理链路
  - 失败文件重新解析

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `requirement.md`
3. `../一期/requirement.md`
4. `../一期/technical_design.md`
5. `technical_design.md`
6. `AGENTS.md`
7. `project_info.md`
8. `docs/组件和数据库约定/middleware_contract.md`
9. `docs/组件和数据库约定/middleware-components/kafka_component.md`
10. `docs/组件和数据库约定/middleware-components/oss_component.md`

## 7. 实现完成后回填

- 实际完成摘要：
- 已新增解析任务表与最新解析文件表结构，Java 端负责创建解析任务、投递解析 MQ、提供手动解析入口、自动解析入口、SSE 进度订阅入口和文件解析结果查询入口。
- 已将解析任务 MQ 定义为与 Python 约定的扁平 snake_case JSON，`task_id` 与解析任务表业务标识保持一致，Markdown 产物 bucket 固定为 `rag-md`。
- 已禁用旧解析结果 MQ 消费链路的默认装配，避免二期中 Java 端继续写解析结果数据。
- 测试结论：
- 已通过服务层和 API 层目标测试，测试明细见后续 `testing_delivery.md`。
- 是否已更新 `project_info.md`：是
