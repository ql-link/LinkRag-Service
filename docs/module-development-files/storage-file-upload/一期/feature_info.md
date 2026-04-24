# 功能信息卡

## 1. 基础信息

- 模块名称：文件上传模块重构
- 当前期次：一期
- 业务域：storage
- 当前状态：需求待审核
- 复杂度等级：L3
- 当前分支：skill-test

## 2. 功能摘要

- 背景：当前项目已经具备数据集、知识原始文件、OSS 组件与部分文件相关链路，但“文件上传”作为用户可见主链路，仍需要以 Java 端为中心重新收敛边界，明确上传入口、数据集归属、文件记录落库、上传结果反馈和错误处理规则。
- 目标：完成 Java 端文件上传主链路重构，使登录用户可以在指定数据集下上传原始知识文件，并得到可追踪、可校验、可回显的上传结果。
- 本期目标：只覆盖 Java 端文件上传主链路，不包含 MQ 解析任务投递、解析结果回写及异步状态编排。
- 本次不做：
  - MQ 解析任务投递
  - 解析结果回写
  - 解析状态流转与补偿
  - 二期扩展链路设计与实现

## 3. 影响范围

- 关联模块：
  - `link-api`
  - `link-service`
  - `link-model`
  - `link-mapper`
- 关联中间件：
  - OSS
- 关联数据库：
  - MySQL
- 关联外部系统：
  - 无强依赖外部系统，本期不接 MQ

## 4. 文档清单

- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`

## 5. 关联功能

- 历史相似功能：
  - 现有知识文件上传与对象存储链路
  - 数据集与知识文件归属链路
- 后续期次规划：
  - 二期补齐 MQ 解析任务投递与解析结果回写
- 依赖的上游功能：
  - 用户登录与鉴权
  - 数据集管理
  - OSS 组件能力
- 受影响的下游功能：
  - 知识文件列表展示
  - 后续文件解析链路

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `requirement.md`
3. `project_info.md`
4. `docs/architecture/middleware_contract.md`
5. `docs/architecture/components/oss_component.md`

## 7. 实现完成后回填

- 实际完成摘要：
- 测试结论：
- 是否已更新 `project_info.md`：否
