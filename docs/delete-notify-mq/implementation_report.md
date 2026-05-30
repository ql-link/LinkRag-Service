# delete-notify-mq 实现报告

- **需求**：issue ql-link/LinkRag-Service#29 第 1 部分（删除通知 Java 半）
- **依据**：已审核 `technical_design.md`（brief / acceptance 已冻结）
- **日期**：2026-05-30
- **结论**：实现完成，全量 `mvn test` BUILD SUCCESS，doc-sync / ai-links 通过。**未宣称可发布**，进入质量审查（code-review-and-quality）。

## 1. 概述

把删除通知从占位升级为真实 MQ 投递：新增删除通知消息契约 `DocumentDeleteNotifyMQ`（`tolink.rag.document_delete`，QUEUE，扁平 JSON snake_case，按 `delete_type` 分 dataset/file 两范围），`DocumentDeleteNotifier` 注入 `ObjectProvider<MQSend>` 真实投递并对失败尽力发（吞掉不外抛），两个删除入口按范围分流调用，并同步契约/架构/集成/测试文档。

## 2. 改动清单

### 生产代码（4）

| 文件 | 动作 | 说明 |
| :--- | :--- | :--- |
| `link-service/.../service/mq/DocumentDeleteNotifyMQ.java` | 新增 | 删除通知消息契约：`MQ_NAME`、`QUEUE`、`getMessage()`+`validate`、`forDataset`/`forFile` 工厂、无参构造（拓扑扫描）、`MsgPayload`（`@JSONField` snake_case） |
| `link-service/.../service/delete/DocumentDeleteNotifier.java` | 修改 | 注入 `ObjectProvider<MQSend>`；占位 `notifyAfterDelete` → 两语义方法 `notifyDatasetDeleted`/`notifyFileDeleted`；私有 `send` try/catch 吞掉（null sender 告警吞掉） |
| `link-service/.../service/impl/DatasetServiceImpl.java` | 修改 | 删除仅为旧载荷的 `selectList`（及局部 `originalFileIds`）；`notifyPythonAfterCommit`→`notifyDatasetDeletedAfterCommit(datasetId,userId)` 调 `notifyDatasetDeleted` |
| `link-service/.../service/impl/document/DocumentFileServiceImpl.java` | 修改 | `notifyPythonAfterCommit`→`notifyFileDeletedAfterCommit(originalFileId,datasetId,userId)` 调 `notifyFileDeleted`（去掉 `List.of` 包裹） |

### 测试（4：新增 2、修改 2）

| 文件 | 动作 | 说明 |
| :--- | :--- | :--- |
| `link-service/.../service/mq/DocumentDeleteNotifyMQTest.java` | 新增 | 序列化字段/snake_case/QUEUE/dataset 省略 `original_file_id`/逐字段校验拒发 |
| `link-service/.../service/delete/DocumentDeleteNotifierTest.java` | 新增 | 两范围投递载荷（`ArgumentCaptor`）+ 发送失败/发送器缺失吞掉不外抛 |
| `link-service/.../service/impl/DatasetServiceImplTest.java` | 修改 | 通知断言→`notifyDatasetDeleted(10,100)`+`never().notifyFileDeleted`；移除多余 `selectList` stub 与孤儿 `buildFile`；回滚/未授权 `never().notifyDatasetDeleted` |
| `link-service/.../service/impl/DocumentFileServiceImplTest.java` | 修改 | 通知断言→`notifyFileDeleted(1,200,100)`；未授权 `never().notifyFileDeleted`；移除未用 `java.util.List` import |

### 文档（5）

| 文件 | 说明 |
| :--- | :--- |
| `docs/reference/mq_contracts.md` | 消息清单加行 + 新增「删除通知字段」段（字段/样例/语义/幂等/尽力发） |
| `docs/architecture/mq_module.md` | 当前消息表加行 + 约定补充（QUEUE/afterCommit/尽力发吞掉/按范围分流） |
| `docs/guides/integration.md` | 协作接口加 `document_delete` 通道；删除链路「占位」→「已落地（含发布协调提醒）」 |
| `docs/architecture/document_file_module.md` | 删除段「通知 Python（占位）」→「（已落地）」 |
| `docs/development/testing.md` | 隐性删除测试条目更新 + 新增删除通知 MQ 测试条目 |

## 3. 与 TD 的偏差

| 偏差 | 原因 / 影响 |
| :--- | :--- |
| **未新增 `DeleteNotifyIntegrationTest`（TD 标注为可选）** | 「发送失败时删除仍成功」由 `DocumentDeleteNotifierTest` 直接证明（notifier 吞掉一切 RuntimeException、绝不外抛）；删除入口在软删之后才调 notifier，notifier 不外抛即删除必完成。整测需 boot 上下文 + `@MockBean MQSend`，成本高且不增证明力，故省略。影响：无（核心兜底已被单测覆盖） |
| **移除 `DatasetServiceImplTest.buildFile` 私有助手** | TD 列了「移除多余 `selectList` stub」；stub 移除后 `buildFile` 成孤儿（仅被这些 stub 引用），一并删除避免未用方法。`DocumentOriginalFile.class` 在 `@BeforeAll` 仍引用，import 保留 |
| **dataset 通知测试加 `times(1)`+`never().notifyFileDeleted`** | 比 TD 文字更显式地覆盖 acceptance「多文件仍一条」「空数据集仍发」「不发 file 范围」，未超边界 |

## 4. 测试与校验结果

- 全量 `mvn test`：**BUILD SUCCESS**（link-service 163、link-api 117，0 失败 0 错误；其余模块无测试）。
- `python3 scripts/check_docs_sync.py --working`：**OK**（17 changed files，no doc-sync issues）。
- `python3 scripts/check_ai_links.py`：**OK**。

## 5. Scenario 覆盖

| acceptance Scenario | 承接测试 |
| :--- | :--- |
| 删文件→file 范围通知 | `DocumentFileServiceImplTest` + `DocumentDeleteNotifierTest` + `DocumentDeleteNotifyMQTest` |
| 删数据集→dataset 范围通知 | `DatasetServiceImplTest` + notifier 测 + MQ 测 |
| 多文件仍一条 dataset 通知 | `DatasetServiceImplTest`（`times(1)`+不含 `original_file_id`） |
| 消息形态不变量（扁平/snake_case/QUEUE/最简） | `DocumentDeleteNotifyMQTest` |
| 回滚不发 + 删除未生效 | `DatasetServiceImplTest`/`DocumentFileServiceImplTest`（异常→`never()`） |
| 发送失败兜底（删除仍成功、不外抛） | `DocumentDeleteNotifierTest`（抛异常/缺 sender 不外抛） |
| 完整性校验缺字段拒发 | `DocumentDeleteNotifyMQTest`（逐字段抛） |
| 未删（不存在/无权）不投递 | 两 Service 测 `never()` |
| 删空数据集仍投递 dataset 通知 | `DatasetServiceImplTest`（无文件→`notifyDatasetDeleted`） |

## 6. 发布注意（硬约束）

`document_delete` 为点对点队列，**Python 消费端未实现**。**Java producer 不可单独上生产**，否则无消费者导致 broker 队列积压。须待 Python 消费端就绪后两端一起发布（发布检查单 / PR 说明显式标注）。代码合入本身安全（不影响现有功能、无 schema 变更）。

## 7. 下一步

进入 `code-review-and-quality` 质量审查；通过后按 `branch-pr-workflow` 提交 PR（PR 说明须标注发布协调硬约束）。
