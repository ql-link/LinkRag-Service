# 功能信息卡

## 1. 基础信息

- 模块名称：文件上传与解析协同重构
- 当前期次：二期
- 业务域：storage
- 当前状态：需求与技术方案待审核
- 复杂度等级：L3
- 当前分支：skill-test

## 2. 功能摘要

- 背景：一期只完成原文件上传链路；二期补齐上传后的解析任务、MQ 投递、Python 解析协同、解析进度展示和解析结果记录。
- 目标：用户可对已上传成功的原文件发起解析，Java 端创建解析任务并投递 MQ，Python 端消费任务后执行解析、更新任务结果并维护最新解析文件。
- 本期目标：
  - 建立解析任务表，记录同一原文件的多次解析历史。
  - 建立解析文件表，记录每个原文件当前最新成功解析结果和成功解析次数。
  - 定义 Java 到 Python 的解析任务 MQ 消息体。
  - 定义 Python 解析进度上报与前端查询链路。
  - 定义解析失败后的可见状态与再次解析入口。
- 本次不做：
  - 不实现向量化、检索、问答消费链路。
  - 不实现解析结果历史版本人工切换。
  - 不修改 MQ、Redis、OSS framework 抽象。

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
  - Redis
- 关联外部系统：
  - Python 解析服务

## 4. 文档清单

- `requirement.md`
- `technical_design.md`

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
8. `docs/architecture/middleware_contract.md`
9. `docs/architecture/middleware-components/kafka_component.md`
10. `docs/architecture/middleware-components/redis_component.md`
11. `docs/architecture/middleware-components/oss_component.md`

## 7. 实现完成后回填

- 实际完成摘要：
- 测试结论：
- 是否已更新 `project_info.md`：否
