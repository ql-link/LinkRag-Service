# ToLink Service 文件上传表结构与业务流程重构一期测试执行与交付记录

> **文档状态：** 已完成，待最终审核
> **项目名称**：ToLink Service
> **模块名称**：文件上传表结构与业务流程重构
> **当前期次**：一期
> **需求文档**：`docs/模块开发文档/文件上传与解析重构/一期/requirement.md`
> **技术文档**：`docs/模块开发文档/文件上传与解析重构/一期/technical_design.md`
> **改造报告**：`docs/模块开发文档/文件上传与解析重构/一期/implementation_report.md`
> **分支名称**：dev
> **执行人：** Codex
> **最后更新时间：** 2026-04-29

---

## 1. 使用说明

本文件记录一期代码实现后的真实验证结果。后续测试人员可按本文用例继续在开发环境或联调环境复测，若发现新增问题，应在第 6 节追加记录。

---

## 2. 测试范围与目标

### 2.1 本次要验证的内容

- 原文件上传成功后写入 `document_original_file`，并初始化唯一 `document_parsed_file`。
- 上传阶段不创建 `document_parse_task`，即使请求携带 `parseImmediately=true`。
- 上传失败、上传超时、OSS 失败写入稳定失败编码。
- 管理端上传配置接口改为读写 Redis key `knowledge:file-upload:config`。
- Redis 配置缺失或测试初始化清理后，上传校验回退 YAML 默认配置。
- 文件列表、解析结果查询、数据集删除不因 `document_parsed_file` 新结构回归。

### 2.2 本次不验证的内容

- 不验证真实 Python 解析执行。
- 不验证二期解析 MQ 投递和 Python 消费。
- 不验证真实 MinIO 远端环境，只验证本地 OSS 组件与上传接口集成。
- 不验证前端页面交互，仅验证接口与服务层行为。

### 2.3 验收项映射

| 验收项 | 对应用例编号 | 是否覆盖 | 备注 |
| :--- | :--- | :--- | :--- |
| 上传成功初始化解析文件记录 | TC-01 | 是 | API 集成测试覆盖 |
| 一期不创建解析任务 | TC-01 | 是 | 断言 `document_parse_task` 为 0 |
| Redis 上传配置读写 | TC-02 | 是 | 管理端接口测试覆盖 |
| 失败原因编码 | TC-E01 / TC-E02 | 是 | OSS 失败和超时编码覆盖 |
| 数据集删除兼容新解析文件表 | TC-R01 | 是 | API 与 Service 测试覆盖 |
| 旧解析结果 MQ 不再写解析文件表 | TC-R02 | 是 | Service 单元测试覆盖 |

---

## 3. 测试前提与环境准备

### 3.1 环境信息

| 项目 | 内容 |
| :--- | :--- |
| 分支 | `dev` |
| 部署环境 | 本地测试 |
| 服务状态 | 未启动完整服务，使用 Maven 自动化测试 |
| 外部依赖 | 本机 Redis `127.0.0.1:6379`；H2 内存库；本地 OSS 测试目录 |
| 相关配置 | `tolink.knowledge-file.max-size-bytes=64`、`tolink.oss.file-root-path=/tmp/tolink-knowledge-file-test`、`qingluopay.mq.vender=none` |

### 3.2 测试前提

- 本机 Redis 可访问；若在沙箱内执行，需要允许测试进程访问 `127.0.0.1:6379`。
- 集成测试初始化会删除 Redis key `knowledge:file-upload:config`，避免运行时配置残留影响上传用例。
- 测试数据库使用 `link-api/src/test/resources/schema.sql`。

### 3.3 测试数据准备

| 数据项 | 用途 | 准备方式 | 备注 |
| :--- | :--- | :--- | :--- |
| 测试用户 | 上传与数据集接口鉴权 | 测试 setup 插入 H2 `sys_user` | 自动准备 |
| 测试数据集 | 上传归属校验 | 测试 setup 插入 H2 `dataset` | 自动准备 |
| 测试文件 | 上传主流程 | `MockMultipartFile` | 自动准备 |
| Redis 上传配置 | 管理端配置验证 | 管理端 PATCH 接口写入 | 自动准备 |

### 3.4 执行方式

- 单元测试
- 集成测试
- 数据库检查
- Redis 检查
- OSS 本地文件检查

---

## 4. 测试执行清单

### 4.1 主流程测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-01 | 原文件上传成功 | 用户和数据集存在，Redis 配置 key 已清理 | 1. 调用上传接口并传 `parseImmediately=true` 2. 查询原文件对象 key 3. 查询解析文件表 4. 查询解析任务表 | 原文件 success；OSS 对象存在；`document_parsed_file` 有 1 条 `parse_count=0`；`document_parse_task` 无记录 | 与预期一致 | 通过 | Codex |
| TC-02 | 管理端修改上传配置 | 管理员登录，Redis 可访问 | 1. PATCH 管理端配置 2. GET 当前配置 | Redis 配置写入成功，返回去重后后缀列表和新大小限制 | 与预期一致 | 通过 | Codex |

### 4.2 异常与边界测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-E01 | OSS 上传失败 | Mock OSS 抛异常 | 执行上传接口 | 原文件 failed，`failure_reason=OSS_UPLOAD_FAILED`，不创建解析文件记录 | 与预期一致 | 通过 | Codex |
| TC-E02 | 上传中超时补偿显式入口 | 存在超时 uploading 记录 | 调用 `markTimeoutUploadsFailed` | 原文件 failed，`failure_reason=UPLOAD_TIMEOUT` | 与预期一致 | 通过 | Codex |
| TC-E03 | 重复上传成功文件 | 已存在同名同后缀 success 原文件 | 再次上传同名同后缀文件 | 返回重复上传错误，不新增原文件事实 | 与预期一致 | 通过 | Codex |
| TC-E04 | Redis 沙箱不可访问 | 默认沙箱阻止本机 Redis | 执行 API 集成测试 | 管理端写 Redis 用例失败，提示需要允许本机 Redis 访问 | 已通过提升权限复测 | 通过 | Codex |

### 4.3 回归检查项

| 检查项 | 检查方式 | 预期结果 | 实际结果 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| 数据集删除兼容解析文件表 | `DatasetControllerTest`、`DatasetServiceImplTest` | 先删解析文件，再删原文件和数据集 | 通过 | 通过 |
| 旧解析结果 MQ 兼容 | `KnowledgeParseResultServiceImplTest` | 只做 documentId 校验和日志，不写解析文件表 | 通过 | 通过 |
| link-service 全量回归 | `mvn -pl link-service -am test` | 42 个测试通过 | 42 个测试通过 | 通过 |
| 上传相关 API 回归 | `mvn -pl link-api -am -Dtest=KnowledgeFileControllerTest,AdminControllerTest,DatasetControllerTest test` | 20 个测试通过 | 20 个测试通过 | 通过 |

---

## 5. 执行证据记录

### 5.1 接口与页面结果

- 自动化接口测试已覆盖上传、文件列表/解析结果查询、数据集删除、管理端配置查询与修改。
- 未执行前端页面测试。

### 5.2 日志与链路记录

- 2026-04-29 16:11 重新执行 `mvn -pl link-service -am test`，`link-service` 及依赖模块测试通过：42 个 service 测试通过，0 失败。
- 2026-04-29 16:12 重新执行 `mvn -pl link-api -am -Dtest=KnowledgeFileControllerTest,AdminControllerTest,DatasetControllerTest test`，提升权限允许访问本机 Redis 后通过：20 个 API 集成测试通过，0 失败。
- 首次在默认沙箱执行 API 集成测试时，Redis 连接 `127.0.0.1:6379` 被阻止，管理端配置写入返回 500。
- 允许本机 Redis 访问后，同一组 API 集成测试通过。
- 测试过程中发现 Redis 运行时配置残留会影响上传测试，已在测试 setup 中清理 `knowledge:file-upload:config`。

### 5.3 数据库 / 缓存 / MQ / OSS 校验结果

- H2 schema 已对齐 `document_parsed_file.latest_parse_task_id` 和 `parse_count=0`。
- 上传成功后断言 `document_parse_task` 无新增记录。
- 上传成功后断言本地 OSS 私有目录存在原文件内容。
- 管理端配置接口写入 Redis 后，GET 可读取更新后的配置。

---

## 6. 问题记录与处理结果

| 编号 | 问题现象 | 影响范围 | 严重级别 | 当前状态 | 处理结果 / 临时结论 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-01 | 旧解析结果服务单测仍断言 Java 写 `document_parsed_file` 解析产物 | service 测试 | 中 | 已修复 | 按新职责调整为旧 MQ 只校验和记录日志 |
| BUG-02 | 数据集删除 service 单测未注入新依赖 `KnowledgeParsedFileMapper` | service 测试 | 中 | 已修复 | 补充 mock，覆盖解析文件删除链路 |
| BUG-03 | Redis 配置残留影响上传测试，导致上传接口读取到上次管理端配置 | API 集成测试 | 中 | 已修复 | 在上传和管理端测试 setup 中清理 `knowledge:file-upload:config` |

---

## 7. 交付结论

### 7.1 测试结论

- 主流程是否通过：通过。
- 异常流程是否通过：通过。
- 回归检查是否通过：通过。

### 7.2 遗留风险

- `KnowledgeFileConfig` 实体和 `KnowledgeFileConfigMapper` 暂未删除，仅退出上传配置主链路。
- 本期不验证真实 MinIO、真实 Python 解析和真实 MQ 投递。
- 二期需要继续重构手动解析链路，明确 `document_parse_task` 创建职责和 `document_parsed_file_id` 消息字段。

### 7.3 是否允许交付

- 是否可交付：有条件可交付。
- 交付前提：空库使用 `docs/db/init.sql` 初始化；已有库先执行 `docs/db/migration/20260429_file_upload_rebuild.sql` 备份并重建文件上传相关表；部署环境 Redis 可用或明确接受 YAML fallback。
- 联调注意事项：前端不应依赖 `bucketName`、`objectKey`、`fileUrl`；上传成功后前端按原文件维度展示“未解析”。
- 发布 / 回滚注意事项：回滚旧代码时需要恢复旧 `knowledge_file_config` 表和旧 `document_parsed_file` 结构。

---

## 8. 回写检查

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填测试结论 | 是 | 当前状态更新为待最终审核 |
| `project_info.md` 已同步更新 | 是 | 最近功能变更摘要已追加 2026-04-29 记录 |
| 本次遗留风险已显式记录 | 是 | 见 7.2 |
| 交付结论已明确 | 是 | 有条件可交付，待最终审核 |
