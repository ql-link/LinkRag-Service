# ToLink Service 文件上传表结构与业务流程重构一期改造报告

> **文档状态：** 代码实现完成，测试交付完成，待最终审核
> **项目名称**：ToLink Service
> **模块名称**：文件上传表结构与业务流程重构（一期）
> **需求文档**：`docs/模块开发文档/文件上传与解析重构/一期/requirement.md`
> **技术文档**：`docs/模块开发文档/文件上传与解析重构/一期/technical_design.md`
> **分支名称**：dev
> **负责人：** Codex
> **最后更新时间：** 2026-04-29

---

## 1. 文档修订记录 (Change Log)

| 版本号 | 修改日期 | 修改内容简述 | 修改人 | 审核人 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-04-29 | 记录一期文件上传重构代码落地、测试适配和中间件契约回写结果 | Codex | 待审核 |

---

## 2. 改造背景与目标 (Overview)

### 2.1 改造背景

本期是文件解析 MQ 重构前的上传基础链路整理，实际实现需要把原文件、解析文件和解析任务三类数据职责拆开，并让上传配置退出 MySQL 表，改为 YAML 默认配置 + Redis 运行时覆盖。

### 2.2 本次改造目标

- 上传成功后由 Java 初始化唯一 `document_parsed_file` 记录，`parse_count=0`，`latest_parse_task_id=null`。
- 上传阶段不再创建 `document_parse_task`，也不投递解析 MQ。
- 上传配置主链路不再依赖 `knowledge_file_config`，改为 Redis key `knowledge:file-upload:config` + YAML fallback。
- 原文件失败原因统一写入稳定编码：`OSS_UPLOAD_FAILED`、`UPLOAD_TIMEOUT`、`PARSED_FILE_INIT_FAILED`、`UNKNOWN_UPLOAD_FAILED`。
- 前端响应不再暴露 OSS bucket、object key、内部 fileUrl。

### 2.3 本报告适用范围

本报告只记录本次实际代码、脚本、测试和公共契约落地情况，不重复展开完整需求和技术方案。

---

## 3. 实际改造清单 (Implementation Inventory)

### 3.1 模块改动清单

| 模块 | 改动类型 | 实际改动内容 | 备注 |
| :--- | :--- | :--- | :--- |
| `link-api` | 修改 | 调整上传、管理端配置、数据集删除相关集成测试和 H2 schema | 一期接口路径保持兼容 |
| `link-service` | 新增/修改 | 新增上传配置 Redis cache service；调整上传主链路、配置读写、旧解析结果兼容逻辑 | 不改 Redis/OSS/MQ framework |
| `link-model` | 修改 | 调整 `KnowledgeParsedFile` 字段映射；隐藏 `KnowledgeFileDTO` 内部 OSS 定位字段 | 旧解析结果字段暂保留为非表字段兼容 |
| `link-mapper` | 未改 | Mapper 接口保持不变 | `KnowledgeFileConfigMapper` 暂未删除，但已退出上传配置主链路 |
| `link-core` | 未纳入本期 | 本期未要求修改核心异常体系 | 工作区存在其他改动，非本报告范围 |
| `link-components` | 未改 | 复用 Redis、OSS、MQ 组件 | 未改 framework |

### 3.2 文件与类改动清单

| 文件/类 | 改动类型 | 实际职责变化 | 备注 |
| :--- | :--- | :--- | :--- |
| `KnowledgeFileServiceImpl` | 修改 | 上传成功后初始化解析文件记录；上传失败写稳定编码；上传阶段不再触发自动解析 | 一期不启用上传超时定时补偿 |
| `KnowledgeFileConfigCacheService` / `Impl` | 新增 | 封装 Redis key、序列化、读写上传配置 | key 为 `knowledge:file-upload:config`，不设 TTL |
| `KnowledgeFileRuntimeConfigServiceImpl` | 修改 | Redis 优先读取配置，缺失/异常/非法时回退 YAML | 每次请求获取配置快照 |
| `AdminKnowledgeFileConfigServiceImpl` | 修改 | 管理端配置写 Redis，不再写 MySQL | Redis 写失败返回配置保存失败 |
| `KnowledgeParsedFile` | 修改 | 对齐目标 `document_parsed_file` 字段 | 旧字段标记 `exist=false` |
| `KnowledgeParseResultServiceImpl` | 修改 | 旧 `parse_result` MQ 仅兼容校验和日志，不再写解析产物表 | 二期由 Python 写任务表 |
| `KnowledgeParseTaskServiceImpl` | 修改 | 查询解析结果时解析文件名按原文件名推导 | 上传链路不再调用自动解析 |
| `schema.sql` / `docs/db/init.sql` | 修改 | 对齐 `document_parsed_file` 新结构，移除 `knowledge_file_config` 初始化 | 空库初始化以 `docs/db/init.sql` 为准 |
| `docs/db/migration/20260429_file_upload_rebuild.sql` | 新增 | 备份旧文件上传相关表后删除并重建新表 | 已有库升级使用该脚本 |

### 3.3 接口与配置改动清单

| 类型 | 名称 | 改动说明 | 影响范围 |
| :--- | :--- | :--- | :--- |
| API | `POST /api/v1/datasets/{datasetId}/files` | 继续接收 `parseImmediately`，但一期忽略，不创建解析任务 | 前端兼容 |
| API | 管理端上传配置接口 | 读写 Redis 配置快照，不再读写 MySQL 配置表 | 管理端兼容 |
| 配置项 | `tolink.knowledge-file.*` | 作为 YAML 默认上传配置 | Redis 不可用时兜底 |
| 定时任务 / 消费者 | 上传超时补偿 | 移除自动定时补偿，仅保留显式方法和测试入口 | 一期失败由用户手动重试 |

### 3.4 数据与中间件改动清单

| 组件 | 名称/Key/Topic/Path | 改动说明 | 是否涉及契约更新 |
| :--- | :--- | :--- | :--- |
| MySQL | `document_original_file` | `failure_reason` 改为稳定失败编码语义 | 是 |
| MySQL | `document_parsed_file` | 上传成功后初始化一对一解析业务记录，不再保存解析产物字段 | 是 |
| MySQL | `knowledge_file_config` | 退出上传配置主链路 | 是 |
| Redis | `knowledge:file-upload:config` | 保存运行时上传配置覆盖值，不设置 TTL | 是 |
| MQ | `tolink.rag.parse_task` | 一期上传链路不投递；二期再接入 | 是 |
| OSS | 原文件对象 key | 复用既有 `original/user-{userId}/dataset-{datasetId}/...` 规则 | 否 |

---

## 4. 实际落地实现说明 (What Was Built)

### 4.1 核心实现路径

```text
KnowledgeFileController.upload
  -> KnowledgeFileServiceImpl.upload
  -> 校验数据集归属和上传配置快照
  -> 创建或复用 document_original_file(uploading)
  -> IOssService.upload2PreviewUrl
  -> 回写 document_original_file(success)
  -> initializeParsedFileIfAbsent(document_parsed_file)
  -> 返回 KnowledgeFileDTO
```

### 4.2 关键实现点

| 实现点 | 实际落地位置 | 处理说明 |
| :--- | :--- | :--- |
| 上传成功初始化解析文件 | `KnowledgeFileServiceImpl.initializeParsedFileIfAbsent` | 先查后插，唯一键并发冲突时重查确认 |
| 四类失败编码 | `KnowledgeFileServiceImpl` | OSS 失败、上传超时、解析文件初始化失败和未知失败分别写入稳定编码 |
| Redis 配置覆盖 | `KnowledgeFileConfigCacheServiceImpl` | Redis 存 JSON 字符串，避免 DTO 时间序列化兼容问题 |
| YAML fallback | `KnowledgeFileRuntimeConfigServiceImpl` | Redis 缺失、异常或配置非法时回退默认配置 |
| 响应字段收敛 | `KnowledgeFileDTO` / `toDTO` | `bucketName`、`objectKey`、`fileUrl` 不对前端输出 |
| 旧解析结果兼容 | `KnowledgeParseResultServiceImpl` | 保留 documentId 校验和日志，不再写 `document_parsed_file` |

### 4.3 关键注释与设计意图

- `KnowledgeFileServiceImpl` 上传类注释说明原文件服务只负责上传事实，解析任务和结果不放入原文件服务扩展。
- `initializeParsedFileIfAbsent` 的并发处理通过唯一键冲突后重查，保障同一原文件只生成一条解析文件记录。
- Redis 配置写入处注释明确运行时覆盖值不设置 TTL，Redis 不可用时由上层决定失败或回退。
- `KnowledgeParseResultServiceImpl` 注释明确旧 MQ 只做日志兼容，避免后续继续把解析产物写回解析文件表。

### 4.4 未纳入实现的部分

- 未实现解析 MQ 投递。
- 未在上传链路创建 `document_parse_task`。
- 未实现 Python 解析任务创建、状态推进和解析产物回写。
- 未实现自动补偿任务、MinIO 对象探测或解析文件自动补建。
- 未删除 `KnowledgeFileConfig` 实体和 `KnowledgeFileConfigMapper`，仅让其退出上传配置主链路，避免扩大本期删除影响面。

---

## 5. 与技术方案差异说明 (Delta From Technical Design)

### 5.1 差异清单

| 技术方案项 | 原方案 | 实际实现 | 差异原因 | 是否已重新确认 |
| :--- | :--- | :--- | :--- | :--- |
| 上传配置表依赖清理 | 配置表、实体、Mapper 退出上传配置主链路 | 主链路已退出；实体和 Mapper 暂保留未删除 | 避免本期扩大删除影响面，后续确认无引用后再清理 | 是 |
| 上传超时补偿 | 明确一期不启用自动补偿 | 移除 `@Scheduled`，保留显式方法供测试和人工调用 | 保留可测试入口，同时符合“一期用户手动重试”口径 | 是 |
| 旧解析结果 MQ | 旧链路不再作为写库主链路 | 服务只校验和记录日志，不写解析文件表 | 与新表职责一致，避免 Java/Python 双写 | 是 |

### 5.2 差异影响分析

- 对上游调用的影响：上传接口路径与主要字段保持兼容，`parseImmediately` 一期不生效。
- 对下游模块的影响：二期解析投递需要基于已初始化的 `document_parsed_file` 继续设计 MQ payload 和任务表写入。
- 对测试范围的影响：旧解析结果写库单测已调整为兼容日志语义；上传和管理端配置集成测试已更新。
- 对发布与回滚的影响：若回滚旧代码，需要恢复 `knowledge_file_config` 表和旧 `document_parsed_file` 字段结构。

---

## 6. 风险、遗留问题与后续事项 (Risks & Follow-up)

### 6.1 当前已知风险

- OSS 已成功但解析文件初始化失败时，当前按上传失败返回并保留对象定位，一期不自动清理对象。
- Redis 上传配置写入失败会导致管理端修改失败，但普通上传会在读取失败时回退 YAML 默认配置。
- 旧手动解析链路仍存在，二期需要继续按新的 `document_parse_task` 创建职责重构。

### 6.2 遗留问题

- `KnowledgeFileConfig` 实体和 `KnowledgeFileConfigMapper` 暂未删除。
- 二期需要明确 `parse_task` MQ 是否补充 `document_parsed_file_id`，并同步 Java/Python 消息契约。
- 解析产物旧数据如已存在，本次迁移只备份不迁入新表；如后续需要恢复或比对，应从备份表人工处理。

### 6.3 后续建议动作

- 进入 `test-and-delivery` 阶段，补齐 `testing_delivery.md` 并更新 `project_info.md`。
- 二期开始前先收敛 `document_parse_task` 由谁创建、MQ payload 字段、幂等和超时重试策略。
- 在确认无运行时依赖后，单独清理 `KnowledgeFileConfig` 实体和 Mapper。

---

## 7. 回写检查 (Update Checklist)

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填实现摘要 | 是 | 已更新当前状态和实现摘要 |
| `middleware_contract.md` 已按需更新 | 是 | 已更新 MySQL、Redis、MQ 约定 |
| `project_info.md` 已按需更新 | 是 | 测试交付阶段已追加 2026-04-29 最近变更 |
| 已通知测试阶段关注实现差异 | 是 | 关注 Redis fallback、失败编码、解析文件初始化和一期不投 MQ |
