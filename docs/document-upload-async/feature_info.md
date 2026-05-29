# feature_info

| 项 | 值 |
| :--- | :--- |
| 需求名 | document-upload-async |
| 中文名 | 文档文件上传异步化（线程池接入）+ 线程池配置规范化 |
| 来源 | GitHub issue ql-link/LinkRag-Service#1「线程池僵尸配置清理 & 异步化场景规划」(P1 + 配置改进) |
| 分支 | feature/document-upload-async（待创建） |
| 当前阶段 | 质量审查 APPROVE（2026-05-30），待提交/PR |
| brief.md | 已冻结（2026-05-29） |
| acceptance.feature | 已冻结（2026-05-29，22 Scenario） |
| technical_design.md | 已审核（2026-05-29） |
| implementation_report.md | 已生成（2026-05-30） |

## 范围决策（来自 issue 评估）

| issue 项 | 本需求处置 |
| :--- | :--- |
| P0 僵尸线程池 | 不删除，**保留并接入**（用户决策） |
| P1 文档上传异步化 | **本次范围** |
| 配置改进（@ConfigurationProperties / env var） | **本次范围** |
| 线程池结构 | **每业务独立池 + 多池就绪**（嵌套 `thread-pool.<池名>.*`，本次仅 document-upload），不共用通用池 |
| P2 数据集删除并行化 | 不做 |
| P3 缓存补偿 / MQ 重试调度 | 不做 |
| `parseNoticeRetryCount` 字段 | 经核实**不存在**，不处理 |
| `CacheConsistencyService` 的 Thread.sleep | 刻意设计，不改 |
| actuator 线程池指标 | 不做，后续单独评估 |
| 在途上传持久性兜底 | **本次范围**：启动清理 + uploading 超时扫描 |
| 前端轮询改造 | **跨端依赖**：前端当前未轮询，需配合改造 |

## acceptance 覆盖

| 分类 | Scenario 数 |
| :--- | :--- |
| 一、同步快速失败（不落记录） | 3 |
| 二、异步上传主流程（uploading→success / 解析投递） | 4 |
| 三、异步失败与背压（OSS 失败 / 池满拒绝） | 2 |
| 四、在途持久性兜底（超时扫描 / 启动清理） | 3 |
| 五、同名重试（复用 failed 行 / 非 failed 拦截） | 3 |
| 六、孤儿对象兜底（OSS 成功 DB 失败打日志） | 1 |
| 七、线程池配置（多池就绪 / @ConfigurationProperties / env 覆盖 / 校验） | 4 |
| 八、不变量（终态写入方 / 快速失败不残留） | 2 |
| 合计（含 4 个 Scenario Outline） | 22 |

## 阶段记录

- 2026-05-29 issue 评估：对照真实代码核实 issue 全部 claims。确认 `customThreadPool` 为僵尸（无 `@Async`/`@Qualifier`/`CompletableFuture` 使用）、`DocumentFileServiceImpl.upload()` 同步阻塞 OSS 上传、`DatasetServiceImpl.delete()` 循环删 OSS 属实；纠正三处不准确：文件服务已由 `KnowledgeFileServiceImpl` 改名 `DocumentFileServiceImpl`、`parseNoticeRetryCount` 字段全仓不存在、`CacheConsistencyService` 的 sleep 是刻意设计。用户选定方向：保留线程池并接入 P1 上传异步化 + 配置规范化。
- 2026-05-29 brief 首版：范围收敛为「上传异步化 + 线程池配置规范化」。关键现状已核实——线程池 `thread-name-prefix` 本就是 `document-file-upload-`（为上传预留）、`uploading/success/failed` 状态机与 list/detail 轮询能力已具备、解析投递已用 afterCommit 模式、上传 API 已定型。§5 留 7 个待确认问题。
- 2026-05-29 关键决策敲定（用户确认 3 项）：①前端当前**未轮询**，本需求为**跨端需求**，需前端配合改为按 `uploadStatus` 轮询；②池满**拒绝 + 标记 failed**（不沿用 CallerRunsPolicy 降级）；③纳入**启动清理 + uploading 超时扫描**兜底。另默认确认：失败首版仅 status+reason+日志、actuator 指标后续做、命名 `document-upload-async`、API 形态不变。§5 已转为「已决策」，仅余实现级细节留待 TD。
- 2026-05-29 失败流梳理补两条边界：④**同名重试**——现重名校验不分状态导致 failed 后原名重试被拦；已决策改为「撞到 failed 则复用该行重置 uploading 重传」，因数据库唯一约束 `uk_dataset_user_name_suffix` 只能复用旧行不能插新行（影响验收）；⑤**孤儿对象**——OSS 成功但 DB 回写失败不可同事务，首版仅打告警日志 + objectKey 留痕，不做补偿删除。
- 2026-05-29 **brief 冻结**：§5 转为「已冻结决策」，无遗留待确认问题。进入 acceptance 阶段（acceptance-generator）。
- 2026-05-29 acceptance 首版：基于冻结 brief 生成 21 个 Scenario（含 4 个 Scenario Outline），八大分类全覆盖已冻结决策。重点把同名重试（复用 failed 行、不撞唯一约束）、池满拒绝标记 failed、uploading 超时兜底、孤儿对象打日志落为可断言 Then。
- 2026-05-29 多池就绪修订（用户决策「每业务独立池 + 多池就绪」）：**修订 brief 冻结决策**——线程池配置由扁平单池改为嵌套 `thread-pool.<池名>.*`，抽通用 PoolProperties + 工厂，每业务一个独立池/各自拒绝策略与线程名，不共用通用池；本次仅实现 document-upload 池（bean `documentUploadExecutor`）。env 占位符名保留（ConfigFileContentTest 不破），属性路径转嵌套（EnvVarOverrideTest 微调）。acceptance §七 增「独立专用池」场景并改嵌套路径，总数 21→22。
- 2026-05-29 **acceptance 冻结**：22 个 Scenario（含 4 Outline）评审通过，与全部已冻结决策对齐。进入 technical_design 阶段（technical-design）。
- 2026-05-29 technical_design 草稿：基于真实代码定方案——IOssService 新增 File 上传重载（核心阻碍点：现仅收 MultipartFile）、ThreadPool 多池就绪（PoolProperties+工厂+documentUploadExecutor/AbortPolicy、删 customThreadPool）、DocumentFileServiceImpl 拆同步阶段+markUploadSuccess/Failed+同名复用守卫更新、新增 DocumentUploadAsyncExecutor/StuckScanner/TempStorage、镜像 DocumentParseStuckScanner 定时扫描。约 8 新增 + ~9 修改文件，22 Scenario 全映射。
- 2026-05-29 **TD 审核通过**：补充「临时目录与容器 multipart 临时目录同卷 → transferTo 走 rename（≈免费）、跨卷才退化为 ≤20MB 拷贝」实现指引（§6.3/§7.2.7/§12）；核实 multipart 现状（max 20MB、file-size-threshold=0 一律落盘）。进入实现阶段（implementation-execution）。
- 2026-05-30 **实现完成**：按 §13 顺序落地（link-core 多池就绪 / 配置迁移 / OSS File 重载 / link-service 异步化 4 新增组件 / 重构 upload）；新增 6 测试类覆盖 22 Scenario，修复 4 个受语义变更影响的既有测试。全量 `mvn test` 通过（link-service 113、link-api 102 全绿，0 失败）。文档同步 6 篇 + .env.example，check_docs_sync/check_ai_links 通过。TD 偏差 1 处：抽 `DocumentUploadStatusWriter` 独立 bean 承载终态回写（破循环依赖 + 避 @Transactional 自调用失效），不改验收语义。已生成 implementation_report.md，进入质量审查。
- 2026-05-30 **质量审查 APPROVE**：1 个 Required（拒绝路径终态回写须 REQUIRES_NEW，否则池满时记录滞留 uploading）已修复并补集成测试 `Should_MarkUploadFailed_When_PoolRejectsTask`；2 个 Suggestion（超时扫描查询建索引/加批量上限）记录待后续。全量 `mvn test` BUILD SUCCESS。待提交/PR（branch-pr-workflow），发布前置：前端轮询改造。
