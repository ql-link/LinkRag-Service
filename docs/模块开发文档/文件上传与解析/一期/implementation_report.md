# 文件上传与解析协同重构一期改造报告

> **文档状态：** 已测试交付，待最终审核
> **项目名称**：toLink-Service
> **模块名称**：文件上传与解析协同重构
> **需求文档**：`docs/模块开发文档/文件上传与解析/一期/requirement.md`
> **技术文档**：`docs/模块开发文档/文件上传与解析/一期/technical_design.md`
> **分支名称**：`skill-test`
> **负责人：** Codex
> **最后更新时间：** 2026-04-25

---

## 1. 文档修订记录 (Change Log)

| 版本号 | 修改日期 | 修改内容简述 | 修改人 | 审核人 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-04-25 | 初始版本创建，记录一期上传链路实际实现 | Codex | 待审核 |

---

## 2. 改造背景与目标 (Overview)

### 2.1 改造背景

本次为 L3 重构，一期先稳定重建原文件上传链路，将解析触发、MQ、Python 回调和解析进度保留到二期。因此需要记录实际代码落地范围、与旧解析链路剥离情况，以及后续测试阶段应关注的边界。

### 2.2 本次改造目标

- 用户在数据集下上传原文件，Java 端写入原文件表并存储到 OSS / MinIO 私有对象。
- 原文件唯一性按 `dataset_id + user_id + original_filename + file_suffix` 控制。
- 上传状态仅保留 `uploading / success / failed`，失败后允许同一唯一键重试并复用原 `object_key`。
- 上传中超过 1 分钟未完成的记录由定时补偿改为 `failed`。
- OSS 上传动作通过专用线程池执行，请求线程等待上传任务最多 30 秒。
- 一期 API 不再创建解析任务，不再投递 MQ，`parseImmediately` 参数保留但不触发解析。

### 2.3 本报告适用范围

本报告只覆盖一期 Java 端上传链路实现，不重复展开需求背景、完整技术设计和测试交付细节。

---

## 3. 实际改造清单 (Implementation Inventory)

### 3.1 模块改动清单

| 模块 | 改动类型 | 实际改动内容 | 备注 |
| :--- | :--- | :--- | :--- |
| `link-api` | 修改 | 上传、列表、详情、删除接口切换到 `/api/v1/datasets/{datasetId}/files` 和 `/api/v1/files/{fileId}` | 移除一期解析任务入口 |
| `link-service` | 修改 | 重写原文件上传状态机、失败重试、超时补偿、删除 OSS 对象 | 保留解析结果相关旧服务为二期待重构代码 |
| `link-model` | 修改 | 一期 DTO 只返回原文件上传事实字段；原文件实体解析投递字段标记为非表字段 | 避免一期表结构继续承载解析状态 |
| `link-mapper` | 未改 | 复用现有 MyBatis-Plus Mapper | 无新增 Mapper |
| `link-components` | 未改 | 复用现有 OSS 抽象 | 本期不改 OSS 组件实现 |
| `docs/db` | 修改 | 更新初始化脚本中原文件表字段与唯一索引 | 测试 schema 同步更新 |

### 3.2 文件与类改动清单

| 文件/类 | 改动类型 | 实际职责变化 | 备注 |
| :--- | :--- | :--- | :--- |
| `KnowledgeFileController` | 修改 | 提供一期文件上传、列表、详情、删除 API | 移除解析任务 API |
| `InternalKnowledgeFileController` | 修改 | 内部下载 URL 调整为 `/api/v1/internal/files/{fileId}/content` | 仍要求服务 Token |
| `KnowledgeFileService` | 修改 | 接口聚焦原文件上传链路，并暴露超时补偿方法 | 删除一期解析任务方法 |
| `KnowledgeFileServiceImpl` | 重写 | 实现上传幂等、失败重试、OSS 写入、状态回写、删除和超时补偿 | 关键状态流转已补注释 |
| `KnowledgeFileUploadExecutorConfig` | 新增 | 提供文件上传专用线程池 | 线程名前缀 `knowledge-file-upload-` |
| `KnowledgeOriginalFile` | 修改 | 原文件表映射只承载上传字段 | 解析字段临时保留为非表字段以兼容二期旧代码编译 |
| `KnowledgeFileDTO` | 修改 | 返回原文件上传字段、bucket、object_key、file_url | 不再返回解析状态字段 |
| `KnowledgeFileControllerTest` | 修改 | 覆盖一期上传主链路和异常链路 | 使用本地 OSS 实现验证文件写入 |
| `ApiLocalOssPreviewController` | 重命名 | API 层本地 OSS 预览 Controller 名称唯一化 | 避免后续与组件历史 Controller 名称冲突 |
| `LinkApplication` | 修改 | 使用 `@SpringBootApplication(scanBasePackages = "com.qingluo.link")` 扫描全项目 Bean | 不再使用正则排除 OSS Controller |
| `pom.xml` | 修改 | Surefire 升级到 2.22.2，允许依赖模块无测试 | 支持 JUnit5 与 reactor 验证 |

### 3.3 接口与配置改动清单

| 类型 | 名称 | 改动说明 | 影响范围 |
| :--- | :--- | :--- | :--- |
| API | `POST /api/v1/datasets/{datasetId}/files` | 上传原文件，`parseImmediately` 暂不触发解析 | 前端上传入口 |
| API | `GET /api/v1/datasets/{datasetId}/files` | 查询数据集下原文件列表，可按 `uploadStatus` 过滤 | 前端文件列表 |
| API | `GET /api/v1/files/{fileId}` | 查询原文件详情 | 前端详情 |
| API | `DELETE /api/v1/files/{fileId}` | 删除原文件记录和 OSS 对象 | 前端删除 |
| API | `GET /api/v1/internal/files/{fileId}/content` | 服务间下载原文件 | 二期 Python 可复用或再调整 |
| 定时任务 | `compensateTimeoutUploads` | 每 60 秒将超过 1 分钟的 uploading 记录改为 failed | 上传超时补偿 |
| 线程池 | `knowledgeFileUploadExecutor` | OSS 上传任务专用线程池，核心 4、最大 16、队列 200 | 上传并发控制 |

### 3.4 数据与中间件改动清单

| 组件 | 名称/Key/Topic/Path | 改动说明 | 是否涉及契约更新 |
| :--- | :--- | :--- | :--- |
| MySQL | `document_original_file` | 一期只记录原文件上传字段；唯一索引调整为数据集、用户、文件名、后缀 | 是 |
| OSS | `rag-raw` / `original/user-{userId}/dataset-{datasetId}/{yyyy}/{MM}/{dd}/{fileId}/{filename}` | 原文件存储路径按技术方案落地，失败重试复用 object_key | 是 |
| MQ | 无 | 一期不投递 MQ | 否 |
| Redis | 无 | 一期不使用 Redis | 否 |

---

## 4. 实际落地实现说明 (What Was Built)

### 4.1 核心实现路径

前端调用上传接口后，Java 端先校验数据集归属和文件限制，再按唯一键查询原文件记录。不存在记录时插入 `uploading`，失败记录重试时复用原记录和原 `object_key`；随后将 OSS 写入提交到 `knowledgeFileUploadExecutor`，请求线程等待最多 30 秒。成功后回写 `success`、`file_url` 和 `is_upload_success=true`，失败或超时则回写 `failed` 并保留记录供后续重试。

### 4.2 关键实现点

| 实现点 | 实际落地位置 | 处理说明 |
| :--- | :--- | :--- |
| 原文件幂等唯一键 | DB schema + `findByUniqueKey` | 使用 `dataset_id + user_id + original_filename + file_suffix` 控制 |
| 失败重试 | `KnowledgeFileServiceImpl.upload` | `failed` 记录允许重试，并复用原 `object_key` 覆盖 OSS 对象 |
| 上传中保护 | `KnowledgeFileServiceImpl.upload` | 未超时的 `uploading` 返回 409，避免重复写入 |
| 多线程上传 | `KnowledgeFileServiceImpl.uploadObjectWithTimeout` + `KnowledgeFileUploadExecutorConfig` | OSS 写入使用专用线程池，避免请求线程直接创建上传线程 |
| 超时补偿 | `markTimeoutUploadsFailed` | 超过 1 分钟仍为 `uploading` 则改为 `failed` |
| 删除一致性 | `delete` | 先删 OSS，再删 DB；DB 删除失败时抛出补偿提示 |

### 4.3 关键注释与设计意图

- 在 OSS 上传失败分支说明“保留原文件记录、重试复用 object_key 并覆盖对象”，避免后续误删失败记录导致幂等链路失效。
- 在线程池上传超时分支说明“即使底层 MinIO 稍后完成，DB 已进入 failed，后续重试覆盖对象”，明确超时与最终对象状态的取舍。
- 在唯一索引异常兜底分支说明“并发插入时重新读取记录，再按状态走重试或拒绝”，说明索引在幂等中的兜底职责。

### 4.4 未纳入实现的部分

- MQ 解析任务投递。
- Python 解析任务表写入与状态更新。
- 解析百分比进度回传。
- 解析文件表最新产物维护。
- 解析失败重试与历史解析任务展示。

---

## 5. 与技术方案差异说明 (Delta From Technical Design)

### 5.1 差异清单

| 技术方案项 | 原方案 | 实际实现 | 差异原因 | 是否已重新确认 |
| :--- | :--- | :--- | :--- | :--- |
| 内部下载 URL | 文档预留服务间文件 URL | 实现为 `/api/v1/internal/files/{fileId}/content`，不再携带 `taskId` | 一期没有解析任务标识，避免原文件表继续依赖 `task_id` | 是 |
| 解析旧代码 | 技术边界为一期不做解析 | 解析结果服务旧类暂未删除，但上传入口不再调用 | 防止二期重构前过度删除造成不必要编译扩散 | 是 |
| 测试插件 | 技术方案未单列 | 父 POM 升级 Surefire 到 2.22.2 并允许依赖模块无测试 | 当前 JUnit5 测试无法被旧 Surefire 正确发现 | 是 |
| 上传线程模型 | 原方案未显式要求线程池 | 新增专用上传线程池，OSS 写入在线程池中执行并设置 30 秒等待 | 根据实现阶段补充要求：文件上传使用多线程，线程创建使用线程池 | 是 |

### 5.2 差异影响分析

- 对上游调用的影响：前端需改用 `/files` 系列接口；`parseImmediately` 一期可继续传，但不会触发解析。
- 对下游模块的影响：二期 Python 若复用内部下载接口，需要按新的 URL 和服务 Token 调用。
- 对测试范围的影响：一期测试只覆盖原文件上传链路，解析 MQ 测试应移入二期重新设计。
- 对发布与回滚的影响：原文件表字段与唯一索引已调整，发布前需要确认是否存在旧数据迁移要求。

---

## 6. 风险、遗留问题与后续事项 (Risks & Follow-up)

### 6.1 当前已知风险

- 旧解析结果服务仍引用原文件表上的 `parse_task_id` 语义，二期需要按“解析任务表由 Python 写入/更新”的新方案重构，否则不能继续复用旧解析链路。
- DB 成功写入 `uploading` 后如果进程崩溃，定时补偿会在 1 分钟后将记录改为 `failed`，但不会主动清理可能已经写入 OSS 的对象；当前按已确认方案允许后续重试覆盖。
- 请求线程等待线程池上传任务超时后会将记录标记为 `failed`；如果底层 OSS 客户端未及时响应中断，可能仍在后台完成对象写入，当前处理策略仍是后续重试覆盖。

### 6.2 遗留问题

- 二期需要重新定义 MQ 消息体、解析任务表、解析产物表和 Python 回调接口。
- 二期需要决定旧解析相关测试是改造、迁移还是删除。

### 6.3 后续建议动作

- 进入测试与交付阶段，补充 `testing_delivery.md`。
- 二期技术设计开始前，先统一 `middleware_contract.md` 中的 MQ、OSS、MySQL 跨端契约。

---

## 7. 回写检查 (Update Checklist)

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填实现摘要 | 是 | 已更新当前状态和实现摘要 |
| `middleware_contract.md` 已按需更新 | 否 | 当前仅实现一期代码，跨模块契约建议测试/交付或二期前统一补 |
| `project_info.md` 已按需更新 | 是 | 已在测试与交付阶段更新 storage 当前能力和最近变更摘要 |
| 已通知测试阶段关注实现差异 | 是 | 见本文第 5、6 节 |
