# parse-retry-and-sparse-vector-java 实现报告

- **日期：** 2026-05-30
- **分支：** feature/parse-retry-and-sparse-vector-java（基于 dev@1c05362）
- **依据：** technical_design.md v1.0（已审核冻结）
- **状态：** 实现 + 测试 + 文档同步完成，待 code-review-and-quality

## 1. 改动清单

### 新增（5）

| 文件 | 说明 |
| :--- | :--- |
| `link-model/.../entity/DocumentParsePipeline.java` | `document_parse_pipeline` 只读实体（pipeline_status / superseded_by_task_id / 终态与审计列） |
| `link-mapper/.../DocumentParsePipelineMapper.java` | BaseMapper |
| `link-service/.../constant/ParsePipelineStatus.java` | 大写状态常量 + 大小写归一/运行中判定工具 |
| `link-service/.../DocumentParseRetryChainService.java` | 重试链回溯接口 |
| `link-service/.../impl/document/DocumentParseRetryChainServiceImpl.java` | 沿 retry_of_task_id 回溯（深度上限 + 防环 + 链断终止） |

### 修改（6 main）

| 文件 | 说明 |
| :--- | :--- |
| `link-model/.../entity/DocumentParsedLog.java` | 删 `task_status`/`failure_reason` 映射，加 `retry_of_task_id` |
| `link-service/.../mq/DocumentParseTaskMQ.java` | 加 `is_retry`/`previous_task_id` + 重试完整性校验 |
| `link-service/.../impl/document/DocumentParseTaskServiceImpl.java` | 入口分类（首次/重试/已成功/运行中）、已成功 409 拒绝、重试 payload 复用 md 坐标、运行中改判 pipeline、结果前端态由 pipeline 推导 |
| `link-service/.../impl/document/DocumentParseResultServiceImpl.java` | 终态一致性校验从 `log.task_status` 改为 `pipeline_status` 归一比较 |
| `link-service/.../impl/document/DocumentParseStuckScanner.java` | 扫描/补推改用 `pipeline_status`（回归 #15） |
| `link-api/src/main/resources/schema.sql` | H2：删两列、加 retry_of_task_id、加 document_parse_pipeline |

### 文档 / 脚本同步（7）

`docs/db/init.sql`、`docs/reference/mysql_schema.md`、`docs/reference/mq_contracts.md`、`docs/architecture/mq_module.md`、`docs/architecture/document_file_module.md`、`docs/guides/integration.md`、`docs/development/testing.md`。

### 测试（新增 3 / 改 5）

- 新增：`DocumentParseTaskServiceImplTest`(8)、`DocumentParseRetryChainServiceImplTest`(6)；`DocumentParseTaskMQTest` 由 2 扩至 7。
- 改造：`DocumentParseResultServiceImplTest`、`DocumentParseStuckScannerTest`、`DocumentParseResultIntegrationTest`、`DocumentFileControllerTest`、`SoftDeleteReuseIntegrationTest`（均迁移到 pipeline 驱动 / 移除 task_status）。
- 无需改：`DocumentParseResultMQTest`、`DocumentParseResultKafkaReceiverTest`（parse_result 消息体不变）。

## 2. 验收 Scenario 覆盖

26 个 Scenario 全部由测试承接（映射见 technical_design.md §10.2）。关键：
- 首次/重试/已成功/运行中分类、重试 md 复用 + previous_task_id、多轮重试指上一轮 → `DocumentParseTaskServiceImplTest`。
- 重试消息完整性（缺 previous_task_id/md_bucket/md_object_key 不发） → `DocumentParseTaskMQTest`。
- parse_result 按新 task_id 转发 + 不回写 + 状态归一 → `DocumentParseResultServiceImplTest` + `DocumentParseResultIntegrationTest`。
- 重试链边界（链长 1 / 链断 / 深度上限 / 防环） → `DocumentParseRetryChainServiceImplTest`。
- 契约对齐（不读 task_status、前端态由 pipeline 推导、卡住扫描改 pipeline） → 控制器/扫描器测试。

## 3. 验证结果

- 全量测试：`link-service` 135、`link-api` 108，**0 失败 / 0 错误 / 0 跳过**，BUILD SUCCESS。
- `python3 scripts/check_docs_sync.py --working` → OK（30 文件）。
- `python3 scripts/check_ai_links.py` → OK。

## 4. 偏离 TD 记录

- 仅一处实现级补充：`DocumentParseTaskServiceImplTest` 因服务用 `LambdaUpdateWrapper.set(DocumentParseFile::...)`（即时解析列名）需 MyBatis-Plus TableInfo 缓存，纯 Mockito 单测无 MyBatis 上下文，故 `@BeforeAll` 调 `TableInfoHelper.initTableInfo(...)` 预热（沿用 `DocumentUploadStatusWriterTest` 既有模式）。不影响验收契约。

## 5. 待联调确认（来自 TD 风险，非阻塞实现）

- **R4**：Java parse_task 仍发字段名 `document_parse_file_id`；Python `ParseTaskPayload` 文档列为 `document_parse_task_id`。保持现字段名未改（现网首解可用→大概率有 alias），**联调需核对 Python pydantic alias**。
- **R5**：判定读主库依赖"单数据源即可写主库"，部署环境需确认未连只读副本。
- `document_parse_pipeline` 的 6 个阶段状态/耗时列由 Python 拥有，Java 只建模读取所需子集；若后续 Java 需读阶段级状态再扩实体。

## 6. 下一步

进入 `code-review-and-quality` 质量门禁；通过后 `branch-pr-workflow` 发 PR。本报告不代表可发布。
