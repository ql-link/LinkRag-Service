# document-upload-async 实现报告

- 日期：2026-05-30
- 输入：brief.md / acceptance.feature / technical_design.md（均已冻结/审核）
- 状态：实现 + 测试完成，全量 `mvn test` 通过；待质量审查（code-review-and-quality）

## 1. 交付范围

按 TD §13 顺序完成「文档上传异步化 + 线程池多池就绪」：

- **link-core 多池就绪**：新增 `PoolProperties`（5 参数 + `validate()` fail-fast）、`ThreadPoolProperties`（`@ConfigurationProperties(prefix=thread-pool)`，嵌套 `documentUpload`）；`ThreadPoolConfig` 去散落 `@Value`，加通用 `buildExecutor(props, handler)`，`@Bean documentUploadExecutor`（AbortPolicy，前缀 `document-file-upload-`），删除僵尸 `customThreadPool`。
- **配置迁移**：三处 `thread-pool` 配置段改嵌套 `thread-pool.document-upload.*`，dev 保留并补齐 5 个 `${THREAD_POOL_*}` 占位符；新增 `tolink.document-file.upload-async.*`（temp-dir / stuck-threshold=10m / scan-interval-ms=60000）。
- **OSS File 上传重载**：`IOssService` 新增 `upload2PreviewUrl(place, File, contentType, key)`；`LocalFileService`（`Files.copy`）、`MinioFileService`（`putObject` + 本地流 + `contentType`）实现。
- **link-service 上传异步化**：`DocumentFileServiceImpl.upload()` 拆为同步阶段（校验/同名分流/物化临时文件/落 uploading/afterCommit 提交异步任务）+ 立即返回 uploading；新增 `DocumentUploadAsyncExecutor`（池线程 OSS 上传→回写→清理）、`DocumentUploadStatusWriter`（终态守卫回写 + 解析投递）、`DocumentUploadTempStorage`（物化/清理/启动清理）、`DocumentUploadStuckScanner`（`@Scheduled` 超时置 failed）。
- **文档同步**：`object_storage_module.md`、`configuration.md`、`document_file_module.md`、`integration.md`、`testing.md`、`api_contracts.md` 已更新；`.env.example` 补 2 个新 env；`check_docs_sync.py --working` 与 `check_ai_links.py` 均通过。

## 2. 与 TD 的偏差（已记录）

- **新增 `DocumentUploadStatusWriter` 承载 `markUploadSuccess`/`markUploadFailed`**（TD 原计划放在 `DocumentFileServiceImpl`）。
  - 原因：`DocumentFileServiceImpl` 需依赖 `DocumentUploadAsyncExecutor` 提交任务，而异步执行器需回写终态——若回写方法留在 `DocumentFileServiceImpl` 会形成循环依赖；且若在同类内自调用 `@Transactional` 方法会因绕过代理而事务失效。抽独立 bean 同时解决两问题，超时扫描也复用其 `markUploadFailed`。
  - 影响：不改变任何冻结的验收语义（守卫更新、解析投递时机、孤儿日志均不变）。

## 3. 测试结果

全量 `mvn test`：**BUILD SUCCESS**（link-service 113、link-api 102，其余模块全绿，0 失败）。

22 个 Scenario 承接：

| 测试类 | 覆盖 Scenario |
| --- | --- |
| `DocumentFileServiceImplTest`（+7 上传用例） | S2/S3/S4/S13/S14/S15/S22 |
| `DocumentUploadStatusWriterTest` | S5/S6/S7/S16/S8/S11 |
| `DocumentUploadAsyncExecutorTest` | S5/S8/S9/S16 |
| `DocumentUploadStuckScannerTest` | S10/S11 |
| `DocumentUploadTempStorageTest` | S4/S12 |
| `ThreadPoolConfigTest` | S17/S18/S20 |
| `EnvVarOverrideTest`（扩 5 参数） | S19 |
| `@SaCheckLogin`（既有框架机制） | S1 |

同时修复因语义变更受影响的既有测试：`DocumentFileControllerTest`（上传响应改 `UPLOADING`、OSS 失败改异步落 failed、用“新线程 join”执行器覆盖 `documentUploadExecutor` 保证确定性）、`ConfigFileContentTest`（补 2 占位符断言）、`EnvTemplateConsistencyTest`（`.env.example` 补 2 env）、`OssApplicationServiceImplTest`（测试桩补 File 重载）。

## 4. 后续事项（不在本次范围）

- **前端轮询（跨端，发布前置）**：前端需改为按 `uploadStatus` 轮询 list/detail，否则用户停在“上传中”。建议前端先行/同窗发布。
- actuator 线程池指标、OSS 孤儿对象补偿删除：均按 brief 决策留作后续。
- 临时目录须与容器 multipart 临时目录同卷（部署注意），使 `transferTo` 走 rename。

## 6. 质量审查修复（2026-05-30）

- **[Required] 拒绝路径终态回写事务传播**：`markUploadSuccess`/`markUploadFailed` 由 `@Transactional`(REQUIRED) 改为 `REQUIRES_NEW`。
  - 问题：池满拒绝（S9）在 `upload()` 事务 afterCommit 阶段（请求线程）调用 `markUploadFailed`，REQUIRED 会参与正在提交完毕的调用方事务，置 failed 不落库 → 记录滞留 `uploading` 直到超时扫描（最长 10 分钟），且期间同名重试被当作“重复”拦截。REQUIRES_NEW 保证终态回写始终独立提交，与调用上下文无关。
  - 补测：`DocumentFileControllerTest.Should_MarkUploadFailed_When_PoolRejectsTask`（可控执行器模拟池满拒绝，断言 DB 记录落 `failed` + `服务繁忙，请稍后重试`）——此前拒绝路径仅有 mock 单测、未验证 afterCommit 提交，是该 bug 的盲区。
- 复测：全量 `mvn test` 仍 BUILD SUCCESS（`DocumentFileControllerTest` 19 用例全过）。

## 5. 结论

实现与测试完成；质量审查发现的 1 个 Required 问题已修复并补测，全量测试通过。
