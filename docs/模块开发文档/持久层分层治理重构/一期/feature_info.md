# 功能信息卡

## 1. 基础信息

- 模块名称：持久层分层治理重构
- 当前期次：一期
- 业务域：governance
- 当前状态：需求讨论待开始
- 复杂度等级：L3
- 当前分支：refactor/update-file-upload-parse

## 2. 功能摘要

- 背景：当前项目虽然使用 `MyBatis-Plus` 作为 ORM 框架，但大量业务查询和更新逻辑直接写在 `Service` 层，`LambdaQueryWrapper`、`LambdaUpdateWrapper`、`UpdateWrapper` 等条件构造已经侵入业务层，导致服务层和持久层耦合偏重。
- 目标：将“业务 SQL 组织方式”从 Service 层内联 Wrapper 逐步收敛到语义化 Mapper 方法和传统 Mapper XML 中，在保留 `MyBatis-Plus` 基础框架能力的前提下，完成持久层分层治理。
- 本期目标：
  - 明确哪些业务模块属于优先治理范围。
  - 明确“允许继续使用 MyBatis-Plus 框架，但业务 SQL 以下沉到 XML 为主”的实现口径。
  - 明确 Service / Mapper / Mapper XML 的职责边界。
  - 为后续分期治理提供模块入口，承接文件上传模块中识别出的持久层侵入问题。
- 本期不做：
  - 不在本卡片阶段直接修改全部业务模块代码。
  - 不强制替换 `MyBatis-Plus` 框架本身。
  - 不把简单主键 CRUD 全部机械改写为 XML。

## 3. 影响范围

- 关联模块：
  - `link-service`
  - `link-mapper`
  - `link-model`
  - 重点业务域包括 `storage`、`dataset`、`chat`、`auth`、`llm-config`
- 关联中间件：
  - MySQL
- 关联代码形态：
  - Service 层中的 `LambdaQueryWrapper` / `QueryWrapper` / `LambdaUpdateWrapper` / `UpdateWrapper`
  - Mapper 接口语义化改造
  - Mapper XML 查询与更新语句

## 4. 文档清单

- `feature_info.md`
- 后续待补：`requirement.md`
- 后续待补：`technical_design.md`
- 后续待补：`implementation_report.md`
- 后续待补：`testing_delivery.md`

## 5. 关联功能

- 来源模块：
  - `文件上传与解析重构/三期`
- 首批重点治理对象：
  - `KnowledgeFileServiceImpl`
  - `KnowledgeParseTaskServiceImpl`
  - `DatasetServiceImpl`
  - `ChatServiceImpl`
  - `AuthServiceImpl`
- 关联规范：
  - `.agents/skills/implementation-execution/SKILL.md`
  - `project_info.md`

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `AGENTS.md`
3. `project_info.md`
4. `.agents/skills/implementation-execution/SKILL.md`
5. `../文件上传与解析重构/三期/feature_info.md`
