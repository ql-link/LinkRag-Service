# 功能信息卡

## 1. 基础信息

- 模块名称：文件上传与解析协同重构
- 当前期次：一期
- 业务域：storage
- 当前状态：测试与交付完成，待最终审核
- 复杂度等级：L3
- 当前分支：skill-test

## 2. 功能摘要

- 背景：当前项目需要先稳定重建原文件上传链路，并为后续“解析触发、解析结果回写”这条跨 Java、MinIO、MQ、Python 的链路预留边界。
- 目标：一期支持用户在数据集下上传原始文件，并记录原文件事实和上传状态；解析任务、MQ、Python 解析和解析进度放到二期。
- 本期目标：
  - 明确原文件上传一期需求边界
  - 明确用户、前端、Java 服务、MinIO、MySQL 在上传链路中的职责划分
  - 明确同一数据集下原文件唯一和上传状态可见的业务规则
- 本次不做：
  - MQ 投递
  - Python 解析
  - 解析百分比进度
  - 解析任务表和解析文件表落地
  - 解析结果如何作为后续检索或问答生效版本

## 3. 影响范围

- 关联模块：
  - `link-api`
  - `link-service`
  - `link-model`
  - `link-mapper`
- 关联中间件：
  - MySQL
  - OSS / MinIO
- 关联外部系统：
  - Python 解析服务（二期）

## 4. 文档清单

- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`

## 5. 关联功能

- 历史相似功能：
  - 既有数据集管理能力
  - 既有 OSS 文件存储能力
  - 既有 MQ 异步协作能力（二期）
- 后续期次规划：
  - 二期补充 MQ 投递、Python 解析、解析百分比进度、解析任务历史、最新解析文件和失败补偿能力
- 依赖的上游功能：
  - 登录与鉴权
  - 数据集权限边界
- 受影响的下游功能：
  - 文件列表展示
  - 文件解析任务查询（二期）
  - 后续知识处理链路

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `requirement.md`
3. `technical_design.md`
4. `AGENTS.md`
5. `project_info.md`
6. `docs/组件和数据库约定/middleware_contract.md`
7. `docs/组件和数据库约定/middleware-components/oss_component.md`
8. `docs/组件和数据库约定/middleware-components/kafka_component.md`
9. `docs/组件和数据库约定/middleware-components/redis_component.md`

## 7. 实现完成后回填

- 已重建一期原文件上传链路，支持数据集内原文件上传、列表、详情、删除。
- 已按 `dataset_id + user_id + original_filename + file_suffix` 建立唯一约束，成功记录拒绝重复上传，失败记录允许重试并复用 `object_key`。
- 已将 OSS 上传动作放入专用线程池执行，请求线程等待线程池任务结果；超时或失败会回写 `failed`。
- 已实现 `uploading` 记录 1 分钟超时补偿。
- 已将 API 本地 OSS 预览 Controller 重命名为 `ApiLocalOssPreviewController`，启动类使用 `@SpringBootApplication(scanBasePackages = "com.qingluo.link")` 扫描全项目 Bean。
- 测试结论：
- `mvn -pl link-api -am -Dtest=KnowledgeFileControllerTest,OssFileControllerTest clean test` 通过，9 个测试全部成功。
- `mvn -pl link-service -am -Dtest=KnowledgeFileServiceImplTest test` 通过，2 个测试全部成功。
- 是否已更新 `project_info.md`：是
