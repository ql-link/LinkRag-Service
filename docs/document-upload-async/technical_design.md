# 文档文件上传异步化（线程池多池就绪）技术设计

- **文档状态：** 技术方案待审核
- **项目名称：** toLink-Service
- **业务域：** 文档文件（document-file）
- **需求名称：** document-upload-async
- **业务输入：** docs/document-upload-async/brief.md（已冻结 2026-05-29）
- **验收输入：** docs/document-upload-async/acceptance.feature（已冻结 2026-05-29，22 Scenario）
- **输出文件：** docs/document-upload-async/technical_design.md
- **最后更新时间：** 2026-05-29

---

## 1. 文档修订记录

| 版本号 | 修改日期 | 修改内容简述 | 来源/提出人 | 审核状态 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-29 | 初始技术设计创建 | brief.md + acceptance.feature | 待审核 |

---

## 2. 输入依据与设计目标

### 2.1 输入依据映射

| 输入来源 | 关键结论 | 技术设计承接方式 |
| :--- | :--- | :--- |
| `brief.md` | 上传 OSS 同步阻塞请求线程；线程池僵尸且本为上传预留；多池就绪；拒绝=标记 failed；持久性兜底；同名重试复用 failed 行；孤儿对象打日志 | §5 总体方案 + §7 方法级实现 |
| `acceptance.feature` | 22 Scenario（同步快速失败/异步主流程/失败背压/在途兜底/同名重试/孤儿/线程池配置/不变量） | §7 方法实现 + §10 测试映射，逐条覆盖 |

### 2.2 技术目标

- 上传接口在记录落 `uploading` 后立即返回；OSS 上传、终态回写、（`parseImmediately`）解析投递在 `documentUploadExecutor` 专用线程池异步完成。
- 线程池由 `@Value` 散落单池改为 `@ConfigurationProperties` 嵌套多池结构 `thread-pool.<池名>.*`，本次仅实现 `document-upload` 池；通用 `PoolProperties` + 工厂，每池独立 bean / 拒绝策略 / 线程名。
- 同步阶段保留所有“用户当场能改”的快速失败（鉴权/归属/格式/大小/文件名/同名非 failed），不落在途产物。
- 失败一律落 `failed` + `failureReason`（OSS 失败 / 池满拒绝 / `uploading` 超时），由前端轮询发现。
- 兜底：启动清理残留临时文件 + 定时扫描 `uploading` 超时置 failed。
- 非目标：P2 数据集删除并行化、P3 缓存补偿/MQ 重试、actuator 指标、前端轮询实现、OSS 孤儿补偿删除。

---

## 3. 改动范围

### 3.1 改动文件目录树

```text
toLink-Service/
├── link-core/src/main/java/com/qingluo/link/core/config/
│   ├── ThreadPoolConfig.java            # [修改] 去 @Value 散落；按 PoolProperties 建 documentUploadExecutor，AbortPolicy；删 customThreadPool
│   ├── ThreadPoolProperties.java        # [新增] @ConfigurationProperties(prefix=thread-pool)，嵌套 documentUpload
│   └── PoolProperties.java              # [新增] 通用池参数 + 校验（max≥core 等）
├── link-components/toLink-components-oss/src/main/java/com/qingluo/link/components/oss/service/
│   ├── IOssService.java                 # [修改] 新增基于本地 File 的 upload2PreviewUrl 重载
│   ├── LocalFileService.java            # [修改] 实现 File 重载（Files.copy）
│   └── MinioFileService.java            # [修改] 实现 File 重载（putObject from FileInputStream）
├── link-service/src/main/java/com/qingluo/link/service/
│   ├── config/DocumentUploadAsyncProperties.java   # [新增] tolink.document-file.upload-async.*（temp-dir/超时/扫描间隔）
│   └── impl/document/
│       ├── DocumentFileServiceImpl.java            # [修改] upload() 同步阶段重构 + markUploadSuccess/Failed + 同名复用 + 物化临时文件
│       ├── DocumentUploadAsyncExecutor.java        # [新增] 线程池任务：OSS 上传→回写→解析投递→清理
│       ├── DocumentUploadStuckScanner.java         # [新增] @Scheduled 扫描 uploading 超时置 failed
│       └── DocumentUploadTempStorage.java          # [新增] 临时文件物化/清理 + 启动清理残留
├── link-api/src/main/resources/
│   ├── application.yml                  # [修改] thread-pool 改嵌套 document-upload；新增 upload-async 配置
│   └── application-dev.yml              # [修改] 同上；保留 ${THREAD_POOL_*} 占位符，补 keep-alive/prefix 占位符
├── link-api/src/test/resources/application.yml      # [修改] thread-pool 改嵌套 document-upload
├── link-api/src/test/java/com/qingluo/link/api/config/
│   ├── EnvVarOverrideTest.java          # [修改] @Value 路径→ thread-pool.document-upload.*；补 5 参数断言
│   └── ConfigFileContentTest.java       # [修改] 占位符文本断言不破；补 keep-alive/prefix 占位符断言
└── docs/{reference/api_contracts.md, guides/configuration.md, architecture/object_storage_module.md, architecture/document_file_module.md} # [修改] 契约/文档同步
```

### 3.2 文件级改动说明

| 文件 | 动作 | 改动目的 | 是否必须 |
| :--- | :--- | :--- | :--- |
| `ThreadPoolProperties.java` / `PoolProperties.java` | 新增 | 多池就绪配置载体 + 校验 | 是 |
| `ThreadPoolConfig.java` | 修改 | 建 `documentUploadExecutor`（AbortPolicy）、删 `customThreadPool` | 是 |
| `IOssService.java` + 两实现 + 测试桩 | 修改 | 支持从本地 File 上传（异步线程无法用请求期 MultipartFile） | 是 |
| `DocumentFileServiceImpl.java` | 修改 | 同步阶段重构 + 异步提交 + 终态回写 + 同名复用 | 是 |
| `DocumentUploadAsyncExecutor.java` | 新增 | 池线程内编排 OSS 上传/回写/解析/清理 | 是 |
| `DocumentUploadStuckScanner.java` | 新增 | uploading 超时兜底 | 是 |
| `DocumentUploadTempStorage.java` | 新增 | 临时文件物化/清理 + 启动清理 | 是 |
| `DocumentUploadAsyncProperties.java` | 新增 | 临时目录/超时阈值/扫描间隔配置 | 是 |
| 三处 yml + 两测试 | 修改 | 配置结构迁移 + 测试对齐 | 是 |
| 四处 docs | 修改 | 契约与文档同步 | 是 |

---

## 4. 当前系统分析

| 类型 | 文件/类/方法 | 当前行为 | 问题或复用点 |
| :--- | :--- | :--- | :--- |
| Service | `DocumentFileServiceImpl.upload()` | `@Transactional(noRollbackFor=BusinessException)`；同步 `ossService.upload2PreviewUrl(MultipartFile)`；先落 `uploading` 后改 `success`/`failed` | 改造主体；状态机/`failureReason`/afterCommit 可复用 |
| Service | `submitAutoParseAfterCommit()` | 用 `TransactionSynchronization.afterCommit` 投递 `submitAutoParseAfterUpload` | 复用此模式提交异步上传任务、解析投递 |
| Service | `initializeParseFileIfAbsent()` | 上传成功后建 `DocumentParseFile` 聚合（幂等，捕获唯一约束） | 移到异步成功分支复用 |
| Service | `assertNoDuplicateOriginalFilename()` | 按 `(user,dataset,filename)` 计数，**不分状态**，failed 也算 → 拦死原名重试 | 改为按状态分流：failed 复用、其余拦截 |
| Controller | `DocumentFileController.upload` | `POST /api/v1/datasets/{datasetId}/files` → `Result<DocumentFileDTO>`，DTO 含 `uploadStatus` | URL/入参不改；语义变（立即 uploading） |
| Component | `IOssService.upload2PreviewUrl` | 仅接受 `MultipartFile` | 需新增 File 重载（核心阻碍点） |
| Component | `LocalFileService`/`MinioFileService` | `transferTo` / `putObject(getInputStream,getSize,getContentType)` | File 重载：`Files.copy` / `putObject(FileInputStream,length,contentType)` |
| Config | `ThreadPoolConfig` | `@Value` 散落 5 字段 + 单 bean `customThreadPool`（CallerRunsPolicy）、无人使用 | 改多池就绪 + 重命名 bean + AbortPolicy |
| Config | `SchedulingConfig` | 已 `@EnableScheduling` | 直接 `@Scheduled` 即可，无需再开 |
| Pattern | `DocumentParseStuckScanner` + `ParseResultStuckProperties` | `@Scheduled(fixedDelayString=...)` 粗筛+逐条精确+单条 try/catch | 上传超时扫描镜像此模式 |
| Entity | `DocumentOriginalFile` | 含 `uploadStatus/failureReason/objectKey/fileUrl/isUploadSuccess/createdAt` | 超时扫描按 `uploadStatus='uploading' AND createdAt<cutoff` |
| 约束 | `uk_dataset_user_name_suffix` | `(dataset_id,user_id,original_filename,file_suffix)` 唯一 | 同名重试只能复用旧行，不能插新行 |
| Test | `EnvVarOverrideTest` | `@Value("${thread-pool.core-pool-size}")` + `THREAD_POOL_CORE_SIZE=10` | 路径随嵌套微调；env 名不变 |
| Test | `ConfigFileContentTest` | 断言 dev 文本含 `${THREAD_POOL_*}` | 占位符文本保留则不破 |

---

## 5. 总体方案设计

### 5.1 总体流程

```mermaid
flowchart TD
    A["upload() 请求线程/事务内"] --> B["鉴权+归属+格式/大小/文件名校验"]
    B --> C{"同名记录?"}
    C -->|failed| D["复用该行: 守卫更新 failed→uploading, 清 reason"]
    C -->|uploading/success| E["抛 400 已存在同名"]
    C -->|无| F["insert status=uploading"]
    D --> G["物化 MultipartFile→本地临时文件"]
    F --> G
    G --> H["注册 afterCommit: 提交任务到 documentUploadExecutor\n注册 afterCompletion: 回滚则清临时文件"]
    H --> I["返回 DTO(uploading)"]
    H -.afterCommit.-> J{"提交线程池"}
    J -->|被拒(池+队列满)| K["markUploadFailed(服务繁忙)+清临时文件"]
    J -->|成功| L["池线程: OSS 上传(File 重载)"]
    L --> M{"OSS 成功?"}
    M -->|否| N["markUploadFailed(上传失败)"]
    M -->|是| O["markUploadSuccess: 守卫更新 success+objectKey/fileUrl"]
    O --> P{"守卫命中?"}
    P -->|0行(已被扫描置failed)| Q["告警日志(孤儿, 含 objectKey)"]
    P -->|是| R["initializeParseFile + (parseImmediately) afterCommit 投递解析"]
    N --> S["清理临时文件"]
    O --> S
    Q --> S
    T["DocumentUploadStuckScanner @Scheduled"] --> U["uploading 超时→markUploadFailed(上传超时)"]
    V["DocumentUploadTempStorage 启动钩子"] --> W["清理临时目录残留"]
```

### 5.2 模块边界

| 模块 | 职责 | 本次是否改动 |
| :--- | :--- | :--- |
| link-core | 线程池多池就绪配置与 bean | 是 |
| link-components-oss | 新增 File 上传能力 | 是 |
| link-service | 上传异步编排、终态回写、同名复用、超时/临时文件兜底 | 是 |
| link-api | 配置 yml 迁移、配置测试对齐 | 是 |
| link-model | 实体/DTO | 否（复用现字段） |
| link-mapper | Mapper | 否（用现有 + 守卫更新 wrapper） |

---

## 6. API、消息与数据设计

### 6.1 API 设计

- `POST /api/v1/datasets/{datasetId}/files`：**URL/入参不变**；返回 `Result<DocumentFileDTO>`，`uploadStatus` 语义由“同步终态/失败抛错”改为“**立即返回 `uploading`**”，终态经 `GET /api/v1/datasets/{datasetId}/files`（按 `uploadStatus` 过滤）或 `GET /api/v1/files/{fileId}` 轮询获取。→ 同步 `api_contracts.md`。
- 同步阶段错误码不变：未登录（sa-token 401 语义）、归属 404、格式/大小/文件名/同名（非 failed）400。

### 6.2 MQ 消息设计

- 无新增/变更消息体。`parseImmediately=true` 的解析任务投递（`submitAutoParseAfterUpload`）由“请求侧 afterCommit”移至“**异步上传成功后**的 `markUploadSuccess` 内 afterCommit”。投递时机变，载体不变。

### 6.3 数据与存储设计

- 表 `document_original_file` 无 DDL 变更；新增基于 `(upload_status, created_at)` 的扫描查询；终态回写改为**状态守卫更新**（`WHERE id=? AND upload_status='uploading'`）保证幂等与并发安全。
- 唯一约束 `uk_dataset_user_name_suffix` 不变；同名重试通过复用旧 failed 行规避新 insert。
- 本地临时文件：新增临时目录（默认 `${java.io.tmpdir}/tolink/document-upload`，可配），与 OSS 私有读缓存目录（`PrivateFileResolver` 用 `oss.filePrivatePath`）**隔离**，避免启动清理误删读缓存；并须与**容器 multipart 临时目录同卷**，使 `transferTo` 走 rename（≈免费，详见 §7.2.7）。
- multipart 现状（已核实）：`spring.servlet.multipart.max-file-size/max-request-size=20MB`、`file-size-threshold` 用默认 0（一律落盘）、无自定义 `location`。即上传内容进 Controller 前已落盘，物化取所有权是 rename 而非重新读流。

---

## 7. 方法级实现方案

### 7.1 方法级变更总表

| 文件 | 类/对象 | 方法/成员 | 动作 | 入参变化 | 返回变化 | 改动目的 | 对应 Scenario |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| ThreadPoolProperties | ThreadPoolProperties | `documentUpload:PoolProperties` | 新增 | - | - | 多池就绪配置 | S17,S18 |
| PoolProperties | PoolProperties | 字段 + `validate()` | 新增 | - | - | 池参数 + max≥core 校验 | S18,S20 |
| ThreadPoolConfig | ThreadPoolConfig | `documentUploadExecutor()` | 新增 | 注入 ThreadPoolProperties | `Executor` | 专用池 AbortPolicy + 前缀 | S17,S18,S19,S20 |
| ThreadPoolConfig | ThreadPoolConfig | `buildExecutor(PoolProperties,RejectedExecutionHandler)` | 新增 | - | `ThreadPoolTaskExecutor` | 通用工厂 + fail-fast 校验 | S18,S20 |
| ThreadPoolConfig | ThreadPoolConfig | `customThreadPool()` | 删除 | - | - | 去僵尸通用池 | S17 |
| IOssService | IOssService | `upload2PreviewUrl(place,File,contentType,key)` | 新增 | File 重载 | `String` | 异步从本地文件上传 | S5,S8 |
| LocalFileService | LocalFileService | 同名重载 | 新增 | - | `String` | Files.copy 实现 | S5 |
| MinioFileService | MinioFileService | 同名重载 | 新增 | - | `String` | putObject(FileInputStream) | S5 |
| DocumentFileServiceImpl | DocumentFileServiceImpl | `upload()` | 修改 | 不变 | 不变(uploading) | 同步阶段重构 + 异步提交 | S1,S2,S3,S4,S13,S14,S15,S22 |
| DocumentFileServiceImpl | DocumentFileServiceImpl | `resolveTargetRecord()` | 新增 | userId/dataset/name/suffix | `DocumentOriginalFile` | 同名复用/拦截/新建分流 | S13,S14,S15 |
| DocumentFileServiceImpl | DocumentFileServiceImpl | `markUploadSuccess()` | 新增 | recordId/objectKey/url/parseImmediately/userId | `void` | 守卫回写 success + 解析投递 + 孤儿日志 | S5,S6,S7,S16 |
| DocumentFileServiceImpl | DocumentFileServiceImpl | `markUploadFailed()` | 新增 | recordId/reason | `void` | 守卫回写 failed | S8,S9,S10,S11 |
| DocumentFileServiceImpl | DocumentFileServiceImpl | `assertNoDuplicateOriginalFilename()` | 删除 | - | - | 被 resolveTargetRecord 取代 | S13,S15 |
| DocumentUploadAsyncExecutor | DocumentUploadAsyncExecutor | `execute()` | 新增 | recordId/tempFile/objectKey/contentType/parseImmediately/userId | `void` | 池线程编排 OSS→回写→清理 | S5,S8,S16 |
| DocumentUploadStuckScanner | DocumentUploadStuckScanner | `scan()` | 新增 | - | `void` | @Scheduled uploading 超时置 failed | S10,S11 |
| DocumentUploadTempStorage | DocumentUploadTempStorage | `materialize(MultipartFile)` | 新增 | MultipartFile | `Path` | 物化临时文件 | S4,S22 |
| DocumentUploadTempStorage | DocumentUploadTempStorage | `delete(Path)` | 新增 | Path | `void` | 终态清理 | S5,S8,S9 |
| DocumentUploadTempStorage | DocumentUploadTempStorage | `cleanupOnStartup()` | 新增 | - | `void` | 启动清理残留（ApplicationRunner） | S12 |
| DocumentUploadAsyncProperties | DocumentUploadAsyncProperties | tempDir/stuckThreshold/scanIntervalMs | 新增 | - | - | 配置载体 | S10,S12 |

### 7.2 逐方法实现设计

#### 7.2.1 `DocumentFileServiceImpl.upload()`（修改）

- 修改后职责：仅同步阶段 + 提交异步任务，立即返回 `uploading`。
- 详细步骤（事务内）：
  1. `assertOwnedDataset`（404）。
  2. `validateFile`（仅用元数据：空/后缀/大小 → 400）；`normalizeOriginalFilename`（非法名 400）；`extractSuffix`。
  3. `record = resolveTargetRecord(userId,datasetId,filename,suffix)`：见 7.2.2。
  4. `Path temp = tempStorage.materialize(file)`（流只读一次；先校验后物化）。
  5. 计算 `objectKey = buildObjectKey(...)`、`bucketName`、回填 record 元数据（size/contentType/bucket）。
  6. 注册 `TransactionSynchronization`：
     - `afterCommit()`：`try { executor.execute(() -> asyncExecutor.execute(record.getId(), temp, objectKey, contentType, parseImmediately, userId)); } catch (RejectedExecutionException ex) { markUploadFailed(record.getId(), "服务繁忙，请稍后重试"); tempStorage.delete(temp); }`
     - `afterCompletion(status)`：`if (status != STATUS_COMMITTED) tempStorage.delete(temp);`（回滚兜底清理）
  7. 返回 `toDTO(record)`（`uploadStatus=uploading`）。
- 事务与异常边界：保留 `@Transactional`；OSS 上传移出事务（在池线程）。同步阶段任一校验失败直接抛 `BusinessException`，物化在校验之后，故失败不残留临时文件/记录（S22）。
- 对应测试：见 §10。

#### 7.2.2 `DocumentFileServiceImpl.resolveTargetRecord()`（新增）

- 职责：同名分流。查 `(user,dataset,filename,suffix)` 现有记录：
  - 无 → new `DocumentOriginalFile`（`uploading`/`isUploadSuccess=false`），`insert`（捕获唯一约束 → 400 同名，覆盖并发）。
  - 有且 `failed` → **守卫更新** `failed→uploading`、清 `failureReason`、刷新元数据（`UPDATE ... WHERE id=? AND upload_status='failed'`）；命中 0 行（并发已被他人复用）→ 视为进行中 → 400。复用同一行 → 不新 insert（S14 规避唯一约束）。
  - 有且 `uploading`/`success` → 抛 400「已存在同名文件」（S15）。
- 并发边界：插入靠唯一约束，复用靠状态守卫更新；二者保证同名并发只一个胜出。

#### 7.2.3 `DocumentUploadAsyncExecutor.execute()`（新增，池线程）

- 步骤：
  1. `String result = ossService.upload2PreviewUrl(PRIVATE, temp.toFile(), contentType, objectKey);`
  2. `if (!hasText(result)) { documentFileService.markUploadFailed(recordId, "文件上传失败，请稍后重试"); }`（S8）
  3. `else { try { documentFileService.markUploadSuccess(recordId, result, parseImmediately, userId); } catch (Exception e) { log.warn("孤儿对象: OSS 成功但 DB 回写失败 objectKey={}", objectKey, e); } }`（S16：不翻转状态，留给扫描兜底）
  4. `finally { tempStorage.delete(temp); }`
- 事务边界：本方法非事务；写库委托给 `markUpload*`（独立 bean，@Transactional 生效），OSS 网络 IO 不占 DB 事务。
- 异常边界：方法内吞异常并落 failed/日志，绝不让异常逃逸出池线程（避免线程池吞异常无痕）。

#### 7.2.4 `DocumentFileServiceImpl.markUploadSuccess()`（新增，@Transactional）

- 步骤：守卫更新 `WHERE id=? AND upload_status='uploading'` set `success/objectKey/fileUrl(=internalBaseUrl+/api/v1/internal/files/{id}/content)/isUploadSuccess=true/failureReason=null`。
  - 命中 0 行（已被超时扫描置 failed）→ `log.warn("孤儿对象: 迟到成功 objectKey={}", objectKey)`，return（不投递解析）（S16 变体）。
  - 命中 → `initializeParseFileIfAbsent(record)`；`if (parseImmediately) submitAutoParseAfterCommit(userId, record)`（S6 仅成功后投递；S7 false 不投递）。
- 对应：S5,S6,S7,S16。

#### 7.2.5 `DocumentFileServiceImpl.markUploadFailed()`（新增，@Transactional）

- 守卫更新 `WHERE id=? AND upload_status='uploading'` set `failed/failureReason/isUploadSuccess=false`。命中 0 行（已 success/已 failed）→ 幂等跳过。供异步 OSS 失败、池满拒绝、超时扫描共用。S8,S9,S10,S11。

#### 7.2.6 `DocumentUploadStuckScanner.scan()`（新增，@Scheduled）

- `@Scheduled(fixedDelayString = "${tolink.document-file.upload-async.scan-interval-ms:60000}")`；镜像 `DocumentParseStuckScanner`。
- 查 `upload_status='uploading' AND created_at < now - stuckThreshold`，逐条 `markUploadFailed(id, "上传超时，请重试")`（守卫更新天然避免误杀刚转终态者），单条 try/catch 隔离。S10,S11。

#### 7.2.7 `DocumentUploadTempStorage`（新增）

- `materialize`：在配置临时目录下生成唯一名（如 `{UUID}.tmp`），调用 `multipartFile.transferTo(tempFile)` 取得文件所有权；返回 Path。**关键**：`file-size-threshold` 默认 0 → 容器在进入 Controller 前已把上传内容落盘为**请求级**临时文件（请求结束即被框架清理）；`transferTo` 对已落盘文件**优先走 `rename`（移动，O(1)、与文件大小无关，≈免费）**，只是把这份请求级临时文件“据为己有”到我们自己的目录，使其不随请求被删；仅当目标目录与容器 multipart 临时目录**跨文件系统**时才退化为字节拷贝（≤20MB，本地盘约几十毫秒）。故临时目录须与容器 multipart 临时目录**同卷**（默认均置于 `java.io.tmpdir` 下），确保走 rename 而非拷贝。`transferTo` 只调用一次（流只读一次）。
- `delete`：`Files.deleteIfExists`，吞 IO 异常仅日志。
- `cleanupOnStartup`：`ApplicationRunner`/`@PostConstruct` 删除临时目录内残留文件（仅该专用目录，S12）。

#### 7.2.8 线程池配置（新增/修改）

- `PoolProperties`：`corePoolSize/maxPoolSize/queueCapacity/keepAliveSeconds/threadNamePrefix` + `validate()`（均为正、`maxPoolSize≥corePoolSize`，否则抛 `IllegalArgumentException`）。
- `ThreadPoolProperties`：`@ConfigurationProperties(prefix="thread-pool")`，字段 `PoolProperties documentUpload`（未来加池=加字段+bean）。
- `ThreadPoolConfig.buildExecutor(props, handler)`：`props.validate()`（fail-fast，bean 创建期失败即启动失败，S20）→ 建 `ThreadPoolTaskExecutor` 套用参数 + handler + `initialize()`。
- `documentUploadExecutor()`：`buildExecutor(props.getDocumentUpload(), new AbortPolicy())`（拒绝抛 `RejectedExecutionException`，S9）；删除 `customThreadPool`（S17）。

---

## 8. 组件与集成设计

- **OSS（契约变更）**：`IOssService` 新增 File 重载，`LocalFileService`（`Files.copy(localFile.toPath(), target, REPLACE_EXISTING)`）、`MinioFileService`（`try(InputStream in=Files.newInputStream(localFile.toPath())) putObject(stream(in, localFile.length(), -1).contentType(contentType))`）实现；`OssApplicationServiceImplTest` 测试桩补该方法。→ 同步 `object_storage_module.md`。需走 `contract-guard`：属组件公共接口扩展（仅新增、向后兼容）。
- **线程池**：`documentUploadExecutor` 经 `@Qualifier("documentUploadExecutor")` 注入 `DocumentFileServiceImpl`。
- **调度**：复用既有 `@EnableScheduling`（`SchedulingConfig`）。

---

## 9. 异常处理与降级策略

| 异常场景 | 处理方式 | 是否抛出 | 是否影响接口/状态 |
| :--- | :--- | :--- | :--- |
| 同步校验失败（无权/格式/大小/名/同名非failed） | 抛 BusinessException，物化前失败不残留 | 是(同步) | 接口 4xx，无记录 |
| 物化临时文件失败 | 抛 BusinessException(500)，清理半成品 | 是(同步) | 接口 500，无在途 |
| 提交线程池被拒（池+队列满） | markUploadFailed(服务繁忙)+清临时 | 否 | 记录 failed（响应已 uploading） |
| 异步 OSS 上传失败 | markUploadFailed(上传失败)+清临时 | 否 | 记录 failed |
| OSS 成功但 DB 回写失败/迟到 | 告警日志含 objectKey，留 uploading | 否 | 由超时扫描兜底 |
| uploading 超时 | 扫描置 failed(上传超时) | 否 | 记录 failed |
| 解析 MQ 投递失败 | 日志；上传仍 success | 否 | 不影响上传成功，可手动解析 |

---

## 10. 测试方案

### 10.1 方法级测试映射

| 被测 | 测试文件 | 对应 Scenario | 断言要点 |
| :--- | :--- | :--- | :--- |
| upload 同步快速失败 | `DocumentFileServiceImplTest`（Mockito）/ controller 层 | S1,S2,S3 | 抛对应码；未调 tempStorage/ossService；无 insert |
| upload 立即返回 uploading | `DocumentFileServiceImplTest`（executor 桩不同步执行） | S4 | 返回 uploadStatus=uploading；objectKey/url 空；提交到 executor |
| markUploadSuccess | `DocumentFileServiceImplTest` | S5,S6,S7,S16 | success+objectKey/url；parseImmediately 决定投递；0行命中→孤儿日志、不投递 |
| asyncExecutor.execute | `DocumentUploadAsyncExecutorTest`（mock oss/service） | S5,S8,S16 | OSS 空→markFailed；成功→markSuccess；回写异常→孤儿日志；finally 清临时 |
| 池满拒绝 | `DocumentFileServiceImplTest`（executor 桩抛 RejectedExecutionException） | S9 | markUploadFailed(服务繁忙)+清临时；不同步执行 OSS |
| 超时扫描 | `DocumentUploadStuckScannerTest` | S10,S11 | 超阈值→failed+可读 reason；未超→不动 |
| 启动清理 | `DocumentUploadTempStorageTest` | S12 | 残留文件被删 |
| 同名复用/拦截 | `DocumentFileServiceImplTest` | S13,S14,S15 | failed→守卫更新复用同一行、无新 insert、不撞唯一约束；uploading/success→400 |
| 不变量 | `DocumentFileServiceImplTest` | S21,S22 | 请求线程只产出 uploading；同步失败不残留记录/临时/不调 OSS |
| 线程池多池/校验 | `ThreadPoolConfigTest`（@SpringBootTest） | S17,S18,S20 | 存在 documentUploadExecutor、前缀 document-file-upload-、无 customThreadPool；非法配置启动失败 |
| env 覆盖 | `EnvVarOverrideTest`（修改+扩展） | S19 | 5 参数经 env 覆盖生效（路径 thread-pool.document-upload.*） |
| 配置文本 | `ConfigFileContentTest` | S19(辅) | dev 文本含 `${THREAD_POOL_*}`（含新增 keep-alive/prefix） |

### 10.2 Scenario 覆盖自检

| Scenario | 承接方法 | 承接测试 | 是否覆盖 |
| :--- | :--- | :--- | :--- |
| S1 未登录 | controller `@SaCheckLogin` | controller/service 测试 | ✅ |
| S2 数据集无权 | upload→assertOwnedDataset | DocumentFileServiceImplTest | ✅ |
| S3 基础校验(Outline) | upload→validateFile/normalize | DocumentFileServiceImplTest | ✅ |
| S4 立即 uploading | upload | DocumentFileServiceImplTest | ✅ |
| S5 异步成功回写 | execute+markUploadSuccess | AsyncExecutorTest/ServiceTest | ✅ |
| S6 parseImmediately=true 投递 | markUploadSuccess | ServiceTest | ✅ |
| S7 parseImmediately=false 不投递 | markUploadSuccess | ServiceTest | ✅ |
| S8 OSS 失败落 failed | execute+markUploadFailed | AsyncExecutorTest | ✅ |
| S9 池满拒绝标记 failed | upload afterCommit catch | ServiceTest | ✅ |
| S10 超时扫描置 failed(Outline) | scan+markUploadFailed | ScannerTest | ✅ |
| S11 超时 reason 可读 | markUploadFailed | ScannerTest | ✅ |
| S12 启动清理 | cleanupOnStartup | TempStorageTest | ✅ |
| S13 同名 failed 复用 | resolveTargetRecord | ServiceTest | ✅ |
| S14 复用不撞唯一约束 | resolveTargetRecord | ServiceTest（验证无新 insert） | ✅ |
| S15 同名非 failed 拦截(Outline) | resolveTargetRecord | ServiceTest | ✅ |
| S16 孤儿对象日志 | execute/markUploadSuccess | AsyncExecutorTest | ✅ |
| S17 独立专用池 | ThreadPoolConfig | ThreadPoolConfigTest | ✅ |
| S18 嵌套 key 绑定 | ThreadPoolProperties | ThreadPoolConfigTest | ✅ |
| S19 env 覆盖(Outline) | yml 占位符 | EnvVarOverrideTest | ✅ |
| S20 非法配置启动失败 | PoolProperties.validate | ThreadPoolConfigTest | ✅ |
| S21 终态只由异步/扫描写 | upload/markUpload*/scan | ServiceTest | ✅ |
| S22 同步失败不残留 | upload | ServiceTest | ✅ |

### 10.3 回归命令

```bash
mvn -pl link-core test
mvn -pl link-components/toLink-components-oss test
mvn -pl link-service test
mvn -pl link-api test
mvn test
python3 scripts/check_docs_sync.py --working
```

---

## 11. 发布与回滚

- **发布前置（跨端）**：前端需先上线“按 `uploadStatus` 轮询 list/detail”，否则用户停在“上传中”。建议前端先行或同窗发布。
- **回滚**：本次为代码+配置变更、无 DDL。回滚即还原代码与三处 yml；`document_original_file` 历史数据兼容（`uploading` 记录由扫描自然收敛或回滚后走同步路径）。
- **灰度**：池 `core/max/queue` 默认 5/10/50 起步，按上传并发观测调参。

---

## 12. 风险与待确认问题

| 风险/问题 | 影响 | 建议处理 |
| :--- | :--- | :--- |
| IOssService 接口扩展 | 触及组件公共契约 + 测试桩 | 仅新增重载、向后兼容；走 contract-guard + 同步 object_storage_module.md |
| 前端未就绪即发布 | 用户停在 uploading | 发布顺序前端先行（§11） |
| 超时阈值误杀在途 | 正常慢上传被置 failed | 默认阈值 10min（远大于 ≤20MB 正常 OSS 耗时），可配；守卫更新避免误翻终态 |
| 临时目录磁盘占用 | 高并发/大文件占满 | 专用临时目录 + 终态即清 + 启动清理；与 OSS 读缓存隔离 |
| 临时目录跨文件系统 | transferTo 退化为字节拷贝（≤20MB，几十 ms） | 临时目录与容器 multipart 临时目录同卷→走 rename；部署/配置文档标注 |
| 池线程内事务自调用陷阱 | @Transactional 失效 | 写库走独立 bean(markUpload* on Service)，executor 仅编排 |
| 待确认：keep-alive/prefix 的 env 名 | 命名一致性 | 暂定 `THREAD_POOL_KEEP_ALIVE_SECONDS`/`THREAD_POOL_THREAD_NAME_PREFIX`，评审确认 |
| 待确认：配置校验是否引入 hibernate-validator | 依赖 | 首版用 `PoolProperties.validate()` 手动 fail-fast，免新依赖 |

---

## 13. 实施顺序

1. link-core：`PoolProperties`/`ThreadPoolProperties`/`ThreadPoolConfig`（删 customThreadPool）→ 跑 `ThreadPoolConfigTest`。
2. 三处 yml 迁移嵌套 + `EnvVarOverrideTest`/`ConfigFileContentTest` 对齐 → 跑 link-api 配置测试。
3. link-components-oss：`IOssService` File 重载 + 两实现 + 测试桩。
4. link-service：`DocumentUploadAsyncProperties`/`DocumentUploadTempStorage` → `DocumentUploadAsyncExecutor` → `DocumentFileServiceImpl` 重构（resolveTargetRecord/markUpload*/upload）→ `DocumentUploadStuckScanner`。
5. 单元/集成测试补齐 22 Scenario。
6. 文档同步（api_contracts/configuration/object_storage_module/document_file_module）+ `check_docs_sync.py`。
7. 全量 `mvn test`。

---

## 14. 人工审核清单

- [ ] 改动文件目录树已确认
- [ ] 方法级变更总表已确认（IOssService 契约扩展、ThreadPool 多池就绪、同名复用守卫更新）
- [ ] 消息/数据/事务边界已确认（OSS 移出事务、写库独立 bean、afterCommit 投递时机迁移）
- [ ] 测试方案已确认（22 Scenario 全覆盖）
- [ ] 发布顺序（前端轮询先行）已知会
