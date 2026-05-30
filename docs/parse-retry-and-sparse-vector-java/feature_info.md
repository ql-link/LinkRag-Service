# feature_info

| 项 | 值 |
| :--- | :--- |
| 需求名 | parse-retry-and-sparse-vector-java |
| 中文名 | Java 端解析失败重试消息与审计字段适配 |
| 来源 | GitHub issue ql-link/LinkRag-Service#16 |
| 关联前序 | knowledge_parse_pipeline_migration、parse-result-consumer-resilience（#15） |
| 分支 | feature/parse-retry-and-sparse-vector-java（已从 dev@1c05362 切出，2026-05-30） |
| 当前阶段 | branch-pr-workflow（实现+测试+文档完成，准备发 PR；R4 留联调核对） |
| brief.md | 已冻结（v3，2026-05-30） |
| acceptance.feature | 已冻结（26 Scenario，2026-05-30） |
| technical_design.md | 已审核冻结（v1.0，2026-05-30） |
| implementation_report.md | 已生成（2026-05-30） |

## 需求范围速览

| 序号 | 需求 | Java 侧性质 | 主要落点 |
| :--- | :--- | :--- | :--- |
| 1 | 首次 / 重试 / 已成功识别 | 全新 | DocumentParseTaskServiceImpl + 新读 pipeline 表 |
| 2 | 重试消息构造 + 复用 Markdown | 改造 | DocumentParseTaskMQ.MsgPayload + buildPayload |
| 3 | parse_result 按新 task_id 更新 | 基本已满足 | DocumentParseResultServiceImpl（回归确认） |
| 4 | 审计字段读取 | 部分新增 | DocumentParsedLog 加 retry_of_task_id + 新 pipeline 只读实体 |
| 5 | 重试链回溯查询 | 全新 | link-service 新增 service + Mapper 回溯 |

## acceptance 覆盖

| 分类 | Scenario 数 |
| :--- | :--- |
| 一 投递入口识别（首次/重试/已成功/运行中） | 6 |
| 二 重试消息构造与复用 Markdown | 3 |
| 三 发送前完整性校验 | 2 |
| 四 parse_result 按新 task_id 处理 | 3 |
| 五 审计字段读取与重试链回溯 | 6 |
| 六 契约对齐（task_status/failure_reason 漂移收口） | 5 |
| 七 不变量 | 1 |
| 合计（含 4 个 Scenario Outline） | 26 |

issue 要求的测试覆盖映射：首次解析（S1/S2/S8）、重试解析（S3/S7/S9）、已成功拒绝（S4）、重试消息字段完整性（S10/S11）、parse_result 新 task_id 更新（S12/S13）、重试链查询边界（S16–S20）——全覆盖。

## 阶段记录

- brief 首版生成（基于 issue #16 + 代码现状调研）：
  - 明确本 issue 虽挂 Python 仓库，但需求全部落在 Java 管理端（本仓库）。
  - 澄清"重试 = 复用 Markdown 的阶段恢复"而非重新解析。
  - 核实现状：`DocumentParseTaskMQ` 无 is_retry/previous_task_id、md 坐标总是新建；`document_post_process_pipeline` 本仓库不存在；`DocumentParsedLog` 无 retry_of_task_id；`retry_count`/`last_retry_at` 在 Java 从未被引用；单数据源无读写分离。
  - 标注本需求对 #15"不维护 retry_of_task_id 链表"边界的受控反转。
  - 留 8 个待确认问题，其中 Q1（稀疏向量 Java 范围）、Q2（pipeline 表契约）为冻结阻塞项。
- brief v2（并入 Python 权威源：ORM `src/models/parse_task.py` + migration 链，合并 0009）：
  - **解掉原 Q1–Q5 + 字段命名**：稀疏向量 Java 零参与；pipeline 表完整 DDL/枚举/键已知；终态权威=`pipeline_status`（大写）；复用旧 log 的 `parsed_*`；`retry_of_task_id` Python 写 Java 读；`is_retry`/`previous_task_id`/`md_*` 命名一致。
  - **修正三处假设**：表已改名 `document_post_process_pipeline`→`document_parse_pipeline`（0007）；重试判定依据是 `is_retry` 布尔而非 previous_task_id 是否存在；`pipeline_status=SUCCESS` 天然含稀疏阶段。
  - **新发现契约漂移 R0**：`document_parsed_log.task_status`/`failure_reason` 已被 Python 0007 删除，但 Java 4 处主代码仍读（含 #15 冻结的 `DocumentParseResultServiceImpl`/`DocumentParseStuckScanner`）。列为冻结阻塞项 Q-A。
  - 剩余开放：Q-A（漂移范围，阻塞）、Q-B（重试链是否出 API）、Q-C（多轮重试 md 取值）、Q-D（单数据源即主库）。
- brief v3（用户拍板 2026-05-30）：
  - **Q-A 决议**：运行库**已应用 0007/0009**（task_status/failure_reason 真已删，现有 Java 映射在真实库上会 `Unknown column`）；漂移对齐**纳入 #16**。
  - 范围因此扩为 6 项：原 issue 5 项 + 附加"契约对齐"（移除两列映射、4 处读取迁到 `pipeline_status`、前端态映射改由 `pipeline_status`+`parsed_object_key` 推导），连带回归 #15 既有测试，属修复性变更。
  - 无剩余冻结阻塞项；Q-B/C/D 给默认取值（否/是/是），用户采纳即可冻结进入 acceptance。
- brief 冻结（2026-05-30）：用户确认切出业务分支 `feature/parse-retry-and-sparse-vector-java`（从 dev@1c05362）并继续；Q-B/C/D 采纳默认（否/是/是）。进入 acceptance 阶段。
- acceptance 草稿（2026-05-30）：26 Scenario（含 4 个 Scenario Outline），覆盖 brief 六条工作流 + 不变量；契约对齐段（六）专门为 task_status/failure_reason 漂移收口 + 回归 #15 卡住扫描立 Scenario。待用户评审冻结后进入 technical-design。
- acceptance 冻结（2026-05-30）：用户确认（"继续"），26 Scenario 评审通过；几处替判（S2 未产出即首次、S5/S6 运行中拒绝、S22/S23 前端态映射、S25 卡住扫描回归）一并采纳。进入 technical-design 阶段。
- technical_design v1.0（2026-05-30，待审核）：基于真实代码（已读 DocumentParseTaskServiceImpl/DocumentParseResultServiceImpl/DocumentParseStuckScanner/DocumentParseSseServiceImpl/DocumentParseTaskMQ/DocumentParseResultKafkaReceiver/实体/DTO/Controller/mq+mysql+api 契约）。
  - 改动：5 新增（DocumentParsePipeline 实体+Mapper、ParsePipelineStatus、DocumentParseRetryChainService(Impl)）+ 6 修改（DocumentParsedLog 瘦身、DocumentParseTaskMQ、TaskServiceImpl、ResultServiceImpl、StuckScanner、schema.sql）+ 4 文档。
  - 关键支点：删 DocumentParsedLog 的 task_status/failure_reason 映射 → 编译器强制收口 9 处 getter 调用 → 全部改 pipeline_status。
  - 26 Scenario 全映射（§10.2）。待确认：R3 已成功拒绝错误码、R4 document_parse_file_id 字段名 alias、R5 单数据源即主库。
- TD 审核冻结（2026-05-30）：用户确认（"冻结，然后下一阶段"），R3/R4/R5 采纳默认（409 友好拒绝 / 保持 document_parse_file_id 字段名 / 单数据源即主库）。进入 implementation-execution。
- implementation 完成（2026-05-30）：按 TD §13 落地，详见 implementation_report.md。
  - 5 新增 + 6 改 main 文件；schema.sql / init.sql 同步；6 reference/architecture/guide 文档同步。
  - 测试：link-service 135 + link-api 108 全绿（0 失败/0 错误）；新增 DocumentParseTaskServiceImplTest(8) / DocumentParseRetryChainServiceImplTest(6) / DocumentParseTaskMQTest 扩至 7；改造 ResultServiceImplTest / StuckScannerTest / 3 个集成测试。
  - doc-sync OK（30 文件）、AI links OK。
  - 偏离 TD：仅一处实现细节——`DocumentParseTaskServiceImplTest` 因 `LambdaUpdateWrapper.set` 需 `@BeforeAll` 调 `TableInfoHelper.initTableInfo` 预热（沿用 DocumentUploadStatusWriterTest 既有模式）；不影响验收。
- 发 PR（2026-05-30）：用户决定 R4（parse_task 的 `document_parse_file_id` vs Python `document_parse_task_id` 别名）留待联调核对，直接发 PR。发前已跑全量测试（243 绿）+ doc-sync + ai-links 全绿。
