---
name: doc-maintenance-sync
description: 当修改 AGENTS.md/CLAUDE.md、docs/reference、docs/architecture、docs/guides、docs/development，或代码变更影响这些文档记录的 API、MySQL schema、MQ 契约、Redis 缓存、OSS、错误码、模块架构、配置时，检查并同步更新对应文档，保证项目文档自动维护。
when_to_use: "当用户要求修改项目文档，或代码/配置/数据库/MQ/API/Redis/OSS 改动会导致 docs 下的架构、约定、参考资料不准确时激活。触发示例：'改了 API 记得更新文档'、'新增错误码'、'调整数据模型'、'修改 MQ 消息结构'、'更新 AGENTS'、'同步文档'、'维护项目说明'"
---

# Documentation Maintenance Sync

## 目标

让项目文档跟随真实代码自动同步，避免 `AGENTS.md`、`docs/reference/`、`docs/architecture/`、`docs/guides/`、`docs/development/` 之间出现过期说明。

本 skill 不是要求每次改代码都重写全部文档，而是要求在相关契约变化时做最小必要同步。

## 触发规则

在以下情况必须使用本 skill：

1. 修改 `AGENTS.md` 或 `CLAUDE.md`
2. 新增、删除、重命名、移动源码、脚本、测试、Skill 或配置入口，导致项目结构文档不准确
3. 修改 HTTP Controller / API DTO，影响接口路径、请求/响应结构
4. 修改 Entity / Mapper / `docs/db/schema.sql`，影响表结构
5. 修改 MQ 消息对象、消费者、生产者、Topic 定义
6. 修改 Redis 缓存 key、过期策略、数据结构
7. 修改 OSS 文件入口、存储路径约定
8. 新增、修改、删除错误码
9. 修改模块边界、组件职责或主要业务链路

以下情况一般不需要同步文档：

1. 只修复局部实现 bug，且没有改变对外接口、模块边界、配置、数据结构或使用方式
2. 只调整测试内部 Mock、断言或临时数据
3. 只修改注释、格式化或日志文案，且不影响文档描述

## 文档映射

按变更内容选择对应文档，不要无差别更新所有文件。

| 变更内容 | 必查文档 |
| --- | --- |
| Controller / API DTO 变更 | `docs/reference/api_contracts.md` |
| Entity / Mapper / DDL 变更 | `docs/reference/mysql_schema.md` + `docs/db/schema.sql` |
| MQ 消息对象、消费者、生产者、Topic 变更 | `docs/reference/mq_contracts.md` + `docs/architecture/mq_module.md` |
| Redis 缓存 key、TTL、结构变更 | `docs/architecture/cache_module.md` |
| OSS 存储路径、文件入口变更 | `docs/architecture/object_storage_module.md` + `docs/architecture/document_file_module.md` |
| 错误码新增或修改 | `docs/reference/error_codes.md` |
| 模块边界、组件职责、业务链路变更 | `docs/architecture/project_structure.md` |
| 配置项（application.yml 等）变更 | `docs/guides/configuration.md` |
| Agent 入口、阅读路径、目录结构 | `AGENTS.md` |
| 当前需求的 brief / acceptance / 技术方案 | `.specs/<需求名>/` 下对应产物 |

## 同步步骤

1. 先识别本次变更影响的契约类型：API、MySQL、MQ、Redis、OSS、错误码、架构、配置。
2. 按"文档映射"读取最少必要文档。
3. 对照真实代码或真实配置，不从记忆补写不确定内容。
4. 只更新失效段落，保持原文档结构和粒度。
5. 完成后运行文档同步校验：

   ```bash
   python3 scripts/check_ai_links.py
   python3 scripts/check_docs_sync.py --working
   ```

6. 用 `git diff` 检查是否仍有旧字段名、旧 topic、旧路径残留。

## 约束

- 不要在文档中写入密钥、Token、真实账号密码或服务器私密凭据。
- 不要把 `AGENTS.md` 重新膨胀成完整知识库；它只保留入口和阅读路径。
- 不要为了文档同步引入与用户请求无关的架构重写。
- 不要把测试报告、一次性排障记录写入稳定架构文档；这类内容放在 PR 描述。
- 以真实代码、`docs/db/schema.sql`、`application.yml` 和当前文档为准，不从记忆臆造。

## 最终回复

最终回复需说明：

1. 同步更新了哪些文档
2. 为什么这些文档需要同步
3. 校验命令是否通过
