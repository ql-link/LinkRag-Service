# 功能信息卡

## 1. 基础信息

- 模块名称：文件上传表结构与业务流程重构
- 当前期次：一期
- 业务域：storage
- 当前状态：测试交付完成，待最终审核
- 复杂度等级：L3
- 当前分支：dev

## 2. 功能摘要

- 背景：原文件上传链路需要先稳定成为后续解析链路的上游事实来源，同时上传配置需要从数据库配置表迁移到 YAML 默认配置 + Redis 动态覆盖，减少运行时表依赖。
- 目标：一期只处理文件上传主链路和上传成功后的解析文件记录初始化，确保每个上传成功原文件都有一条一对一的解析文件记录。
- 本期目标：
  - 原文件表只记录原文件上传事实、归属关系和原文件对象定位。
  - Java 在原文件上传成功后创建或确认 `document_parsed_file` 记录。
  - `document_parsed_file` 初始表示“未解析”，解析次数为 0，最新任务 ID 为空。
  - 将知识文件上传限制配置从 `knowledge_file_config` 迁移为 YAML 默认配置 + Redis 动态覆盖。
- 已实现摘要：
  - 上传成功后由 Java 创建或确认 `document_parsed_file`，初始 `parse_count=0`、`latest_parse_task_id=null`。
  - 上传阶段不再创建 `document_parse_task`，不再因为 `parseImmediately=true` 触发 MQ。
  - 上传配置读写切换为 Redis key `knowledge:file-upload:config`，Redis 缺失/异常/非法时上传校验回退 YAML 默认配置。
  - 原文件失败原因统一写入稳定编码：`OSS_UPLOAD_FAILED`、`UPLOAD_TIMEOUT`、`PARSED_FILE_INIT_FAILED`、`UNKNOWN_UPLOAD_FAILED`。
  - 前端文件响应不再输出 OSS bucket、object key、内部 fileUrl。
- 本期不做：
  - 不发送解析 MQ。
  - 不创建 `document_parse_task`。
  - 不接入 Python 解析执行。
  - 不设计解析失败、解析超时和解析结果回写细节。

## 3. 影响范围

- 关联模块：
  - Java 文件上传服务
  - Java 管理端上传配置能力
  - 前端文件列表展示
- 关联中间件：
  - MySQL
  - MinIO
  - Redis
- 关联数据库：
  - `document_original_file`
  - `document_parsed_file`
  - `document_parse_task`（一期不写入，仅保留表结构和后续边界）
- 关联缓存：
  - `knowledge:file-upload:config`

## 4. 文档清单

- `feature_info.md`
- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`

## 5. 关联功能

- 上游功能：
  - 登录与鉴权
  - 数据集权限边界
  - OSS / MinIO 原文件存储能力
- 下游功能：
  - 二期解析 MQ 投递
  - Python 解析任务日志创建

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `requirement.md`
3. `technical_design.md`
4. `implementation_report.md`
5. `testing_delivery.md`
6. `docs/db/init.sql`
7. `AGENTS.md`
8. `project_info.md`
9. `docs/组件和数据库约定/middleware_contract.md`
