# ToLink Service 文件上传与解析协同重构一期测试执行与交付记录

> **文档状态：** 已完成，待最终审核
> **项目名称**：ToLink Service
> **模块名称**：文件上传与解析协同重构
> **当前期次**：一期
> **需求文档**：`docs/模块开发文档/文件上传与解析/一期/requirement.md`
> **技术文档**：`docs/模块开发文档/文件上传与解析/一期/technical_design.md`
> **改造报告**：`docs/模块开发文档/文件上传与解析/一期/implementation_report.md`
> **分支名称**：`skill-test`
> **执行人：** Codex
> **最后更新时间：** 2026-04-26

---

## 1. 使用说明

本文件记录一期原文件上传链路的可执行测试项与实际执行结果。

执行原则：

- 只验证一期范围：原文件上传、上传状态、失败重试、超时补偿、文件查询与删除。
- 不验证二期范围：MQ 投递、Python 解析、解析百分比进度、解析任务表和解析产物表。
- 所有“通过”结论均基于本地 Maven 测试命令实际执行结果。

---

## 2. 测试范围与目标

### 2.1 本次要验证的内容

- 用户可在有权限的数据集下上传原文件。
- 原文件写入 `rag-raw` 对应对象路径，并在 `document_original_file` 中记录上传状态。
- 同一用户、同一数据集、同一文件名、同一后缀的成功文件拒绝重复上传。
- 上传失败记录允许再次上传，并复用原 `object_key`。
- 上传中的记录超过 1 分钟后可被补偿为 `failed`。
- OSS 上传通过 `knowledgeFileUploadExecutor` 线程池执行。
- 文件列表、详情、删除接口可用。
- OSS 公共文件上传与预览旧能力不因启动类和 Controller 重命名受影响。
- 删除链路中的 OSS 删除失败、DB 删除失败补偿分支可按预期抛出业务异常。

### 2.2 本次不验证的内容

- MQ 解析任务投递。
- Python 解析服务消费和回调。
- 解析进度百分比。
- 解析任务表、解析产物表、最新解析文件维护。
- 前端批量上传页面真实交互。
- 生产 MinIO、MySQL、Redis、MQ 联调。

### 2.3 验收项映射

| 验收项 | 对应用例编号 | 是否覆盖 | 备注 |
| :--- | :--- | :--- | :--- |
| 原文件上传 | TC-01 | 是 | 覆盖 DB 记录、OSS 对象路径、返回 DTO |
| 原文件唯一 | TC-E01 | 是 | 成功记录重复上传返回业务错误 |
| 失败上传重试 | TC-E02 | 是 | 复用失败记录和 `object_key` |
| 上传状态反馈 | TC-01、TC-E03、TC-E04 | 是 | 覆盖 `success`、`failed`、`uploading` 超时 |
| 多线程上传 | TC-02 | 是 | 验证上传线程名前缀 |
| 文件列表详情删除 | TC-03 | 是 | 覆盖列表、详情、删除主链路 |
| 删除补偿 | TC-E05、TC-E06 | 是 | 覆盖 OSS 删除失败与 DB 删除失败 |
| 解析链路不触发 | RT-02 | 是 | 一期不创建解析任务、不投递 MQ |

---

## 3. 测试前提与环境准备

### 3.1 环境信息

| 项目 | 内容 |
| :--- | :--- |
| 分支 | `skill-test` |
| 部署环境 | 本地 Maven 测试环境 |
| JDK | Java 17 |
| 构建工具 | Maven |
| 外部依赖 | 测试使用本地 H2 / Mock / 本地 OSS 路径，不依赖真实生产 MinIO |
| 相关配置 | Surefire 2.22.2，支持 JUnit5 和 reactor 依赖模块无测试 |

### 3.2 测试前提

- `document_original_file` 测试 schema 已包含一期字段和唯一索引。
- `KnowledgeFileControllerTest` 使用测试登录上下文与测试数据集数据。
- OSS 上传通过项目现有 OSS 抽象写入测试路径。
- Service 层删除补偿测试通过 Mock 隔离 OSS 和 DB 行为。

### 3.3 测试数据准备

| 数据项 | 用途 | 准备方式 | 备注 |
| :--- | :--- | :--- | :--- |
| 测试用户 | 模拟当前登录用户 | 测试用例写入 / Mock 登录上下文 | 覆盖 userId 维度 |
| 测试数据集 | 验证数据集归属 | 测试 schema / 用例准备 | 覆盖 datasetId 维度 |
| `Guide.MD`、`duplicate.md`、`retry.txt` 等文件 | 上传、重复、重试、大小写场景 | MockMultipartFile | 文件名大小写敏感，后缀小写存储 |
| 失败原文件记录 | 失败重试 | 测试用例预置 `failed` 记录 | 验证复用 object_key |
| 超时 uploading 记录 | 超时补偿 | 测试用例预置过期 `updated_at` | 验证 1 分钟补偿规则 |

### 3.4 执行方式

- 集成测试：Spring Boot + MockMvc + 测试数据库。
- 单元测试：Service 层 Mock OSS / Mapper 分支。
- 日志检查：确认预期异常分支日志不导致测试失败。
- 数据库检查：通过测试断言校验 DB 记录字段。
- OSS 检查：通过测试断言校验对象 key 和写入内容。

---

## 4. 测试执行清单

### 4.1 主流程测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-01 | 上传合法原文件 | 用户拥有目标数据集 | 1. 调用 `POST /api/v1/datasets/{datasetId}/files` 上传文件 2. 查询 DB 记录 3. 校验 OSS 对象 | 返回 `success`，DB 写入 `rag-raw` 和 `object_key`，OSS 内容存在 | `KnowledgeFileControllerTest` 覆盖，通过 | 通过 | Codex |
| TC-02 | 上传使用线程池 | 上传接口可用 | 1. 上传文件 2. 在 OSS Mock 中记录线程名 | 上传动作运行在线程名前缀 `knowledge-file-upload-` 的线程中 | `Should_UseUploadThreadPool_When_UploadingOriginalFile` 通过 | 通过 | Codex |
| TC-03 | 列表、详情、删除 | 已上传成功文件 | 1. 查询列表 2. 查询详情 3. 删除文件 4. 校验 DB 删除 | 列表和详情返回当前用户文件，删除后记录移除 | `Should_ListDetailAndDeleteUploadedFile_When_FileBelongsToCurrentUser` 通过 | 通过 | Codex |

### 4.2 异常与边界测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-E01 | 成功文件重复上传 | 已存在同用户同数据集同文件名同后缀 `success` 记录 | 再次上传同名同后缀文件 | 返回重复上传业务错误，不创建新记录 | `Should_RejectUpload_When_SameUserDatasetFilenameAndSuffixAlreadySucceeded` 通过 | 通过 | Codex |
| TC-E02 | 失败文件重试 | 已存在同唯一键 `failed` 记录和历史 `object_key` | 再次上传同名同后缀文件 | 复用原记录和原 `object_key`，成功后变为 `success` | `Should_ReuseFailedRecordAndObjectKey_When_RetryUploadSucceeds` 通过 | 通过 | Codex |
| TC-E03 | 上传超时补偿 | 已存在超过 1 分钟的 `uploading` 记录 | 执行 `markTimeoutUploadsFailed` | 记录变为 `failed`，失败原因是上传超时 | `Should_ConvertExpiredUploadingRecordToFailed_When_TimeoutCompensationRuns` 通过 | 通过 | Codex |
| TC-E04 | OSS 上传失败 | OSS 上传抛出异常 | 调用上传接口 | DB 记录变为 `failed`，接口返回上传失败 | `Should_MarkRecordFailed_When_OssUploadFails` 通过 | 通过 | Codex |
| TC-E05 | OSS 删除失败 | OSS 删除返回 false | 调用删除服务 | DB 不删除，抛出业务异常 | `Should_NotDeleteDatabase_When_OssDeleteFails` 通过 | 通过 | Codex |
| TC-E06 | OSS 删除成功但 DB 删除失败 | OSS 删除成功，DB 删除抛异常 | 调用删除服务 | 抛出补偿提示异常 | `Should_ThrowCompensationException_When_DatabaseDeleteFailsAfterOssDelete` 通过 | 通过 | Codex |

### 4.3 回归检查项

| 检查项 | 检查方式 | 预期结果 | 实际结果 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| API 应用启动扫描范围 | `KnowledgeFileControllerTest,OssFileControllerTest clean test` | `service/core/components/api` 均被扫描，应用上下文可启动 | 第一次移除扫描后失败，改为 `@SpringBootApplication(scanBasePackages = "com.qingluo.link")` 后通过 | 通过 |
| OSS 公共上传与预览旧能力 | `OssFileControllerTest` | 公共上传与预览不受 API 本地预览 Controller 重命名影响 | 2 个用例通过 | 通过 |
| 解析链路一期不触发 | 代码边界与测试覆盖 | 上传接口不创建解析任务、不投递 MQ | 上传 Service 仅处理原文件状态，`parseImmediately` 一期兼容接收但不触发解析 | 通过 |
| JUnit5 测试发现 | Maven Surefire | JUnit5 测试可被执行，依赖模块无测试不失败 | Surefire 2.22.2 后测试正常执行 | 通过 |

---

## 5. 执行证据记录

### 5.1 命令执行结果

```bash
mvn -pl link-api -am -Dtest=KnowledgeFileControllerTest,OssFileControllerTest clean test
```

执行结果：

- `KnowledgeFileControllerTest`：7 个用例通过。
- `OssFileControllerTest`：2 个用例通过。
- Reactor 结果：`BUILD SUCCESS`。
- 执行时间：2026-04-26 10:29:21 +08:00。

```bash
mvn -pl link-service -am -Dtest=KnowledgeFileServiceImplTest test
```

执行结果：

- `KnowledgeFileServiceImplTest`：2 个用例通过。
- Reactor 结果：`BUILD SUCCESS`。
- 执行时间：2026-04-26 10:29:30 +08:00。

### 5.2 日志与链路记录

- `KnowledgeFileServiceImplTest` 中出现的两条 ERROR 日志属于测试主动模拟的负向分支：
  - DB 删除失败后的补偿异常。
  - OSS 删除失败后的业务异常。
- 两个负向分支均已通过断言验证，不属于未处理异常。

### 5.3 数据库 / 缓存 / MQ / OSS 校验结果

- MySQL：测试 schema 中 `document_original_file` 已按一期字段执行，测试断言覆盖 `upload_status`、`bucket_name`、`object_key`、`file_url`、`failure_reason`。
- OSS：测试覆盖对象 key 生成和上传内容写入；原文件 bucket 业务名为 `rag-raw`。
- MQ：一期不投递 MQ，本次未启动 MQ，也未验证 MQ 消息体。
- Redis：一期上传链路不使用 Redis，本次未验证 Redis。

---

## 6. 问题记录与处理结果

| 编号 | 问题现象 | 影响范围 | 严重级别 | 当前状态 | 处理结果 / 临时结论 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-01 | 去掉 `@ComponentScan` 后，应用启动只扫描 `com.qingluo.link.api`，导致 `AdminUserService` 等 Service Bean 找不到 | API 应用启动与集成测试 | 高 | 已修复 | 改为 `@SpringBootApplication(scanBasePackages = "com.qingluo.link")`，保留全项目扫描范围，不再使用 OSS Controller 排除规则 |
| BUG-02 | API 层本地 OSS 预览 Controller 名称可能与组件历史 Controller 名称冲突 | Spring Bean 命名与后续组件演进 | 中 | 已修复 | 将 `LocalOssPreviewController` 重命名为 `ApiLocalOssPreviewController` |
| BUG-03 | 旧 Surefire 版本无法正确执行 JUnit5 测试，且依赖模块无测试会导致构建中断 | Maven 测试执行 | 高 | 已修复 | 父 POM 升级 Surefire 到 2.22.2，并设置 `failIfNoTests=false` |

---

## 7. 交付结论

### 7.1 测试结论

- 主流程是否通过：通过。上传、线程池上传、列表、详情、删除均有自动化测试覆盖。
- 异常流程是否通过：通过。重复上传、失败重试、上传失败、超时补偿、删除补偿均有测试覆盖。
- 回归检查是否通过：通过。OSS 公共上传与预览用例通过，API 应用上下文可正常启动。

### 7.2 遗留风险

- 未执行真实 MinIO 环境联调；当前结论基于项目 OSS 抽象和测试路径。
- 未执行前端真实批量上传交互验证；前端本地上传进度展示仍需前端侧联调。
- 旧解析相关服务仍在仓库中，二期需要按解析任务表、解析产物表和 Python 回调重新梳理，不能直接沿用旧解析字段语义。
- `middleware_contract.md` 尚未系统补齐本次一期 MySQL/OSS 契约，建议最终审核或二期开始前补充。

### 7.3 是否允许交付

- 是否可交付：有条件可交付。
- 交付前提：人工审核本测试交付文档，并接受“真实 MinIO 联调、前端批量上传交互、二期解析链路”未在一期自动化中覆盖。
- 联调注意事项：前端上传接口传 `parseImmediately` 不会触发解析；前端应只展示原文件上传结果。
- 发布注意事项：发布前确认目标环境 `document_original_file` 表结构和唯一索引已按一期 DDL 更新。
- 回滚注意事项：若回滚到旧解析上传链路，需同步回滚表字段和接口调用方，否则旧代码可能依赖已移除的解析投递字段。

---

## 8. 回写检查

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填测试结论 | 是 | 当前状态更新为待最终审核 |
| `project_info.md` 已同步更新 | 是 | 更新 storage 能力和最近变更摘要 |
| 本次遗留风险已显式记录 | 是 | 见第 7.2 节 |
| 交付结论已明确 | 是 | 有条件可交付，待人工最终审核 |
