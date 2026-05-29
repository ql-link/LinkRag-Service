# soft-delete-dataset-file 实现报告

- **需求**：数据集/文件删除改为隐性删除（软删保留原文件 + 预留 MQ 通知 Python 删产物）
- **来源**：GitHub issue ql-link/LinkRag-Service#27
- **分支**：feature/soft-delete-dataset-file
- **完成日期**：2026-05-30
- **依据**：brief.md（已冻结）、acceptance.feature（已冻结，21 Scenario）、technical_design.md（已审核）

## 1. 实现内容（按 TD §13 顺序）

1. **实体（link-model）**
   - `Dataset`、`DocumentOriginalFile`：新增 `@TableLogic @TableField("is_deleted") Boolean isDeleted=false` + `@TableField("deleted_seq") Long deletedSeq=0L`。
   - `ChatConversation`：移除 `isDeleted` 字段 + `@TableLogic` + 相关 import。
2. **DDL 双源（数据可清空、无增量迁移、直接新结构）**
   - `docs/db/init.sql` + `link-api/src/main/resources/schema.sql`：
     - `dataset` +`is_deleted`+`deleted_seq`；唯一键 → `uk_dataset_user_name_seq (user_id, name, deleted_seq)`。
     - `document_original_file` +`is_deleted`+`deleted_seq`；唯一键 → `uk_dof_name_suffix_seq (dataset_id, user_id, original_filename, file_suffix, deleted_seq)`。
     - `chat_conversation` 去 `is_deleted`；索引 `idx_chat_conversation_user_active_list` 去 `is_deleted`（H2 侧索引本就不含，无需改）。
3. **`DocumentDeleteNotifier`（新增，link-service `service/delete/`）**：占位发送点，`notifyAfterDelete(ids, datasetId, userId)` 仅留痕日志 + TODO，不落 MQ producer。
4. **`DatasetServiceImpl.delete()` 重构**：去 OSS 物理删/evict；收集名下活文件 id；软删名下原文件（`update set is_deleted=1, setSql("deleted_seq = id")`）；物理删会话+消息；软删数据集（`set is_deleted=1, deleted_seq=自身id`）；afterCommit 调 notifier（无事务则直接调）。移除旧 OSS 补偿语义。
5. **`DocumentFileServiceImpl.delete()` 重构**：去 OSS 物理删/evict；软删该文件（`set is_deleted=1, deleted_seq=自身id`）；**移除 `deleteParseRecords` 调用与方法**（parse 两表交 Python）；afterCommit 调 notifier。`resolveTargetRecord`/`openOriginalFile`/`getOwnedFile` 不改（`@TableLogic` 自动过滤）。
6. **文档同步**：`mysql_schema.md`（删除语义节）、`document_file_module.md`（删除节）、`object_storage_module.md`（删除策略）、`integration.md`（隐性删除联调点）、`testing.md`（隐性删除测试约定）。

## 2. 与 TD 的偏差

| 偏差 | 说明 | 影响 |
| :--- | :--- | :--- |
| TD §10 把 `DatasetControllerTest`/`DocumentFileControllerTest` 列为“回归”，实际需**重写删除断言** | 两者原断言旧硬删/删 OSS 语义（`datasetCount==0`、`fileCount==0`、`chat_conversation ... is_deleted=false`、`Files.exists(privateFile)==false`、`parseFileCount==0`），均与隐性删除冲突 | 已重写为软删语义（物理保留+活计数 0、OSS 文件保留、parse 计数不变、会话/消息物理删）；不改验收语义 |
| `DatasetServiceImplTest` 新增 `@BeforeAll` 初始化 MP TableInfo | 纯 Mockito 单测构建 `LambdaUpdateWrapper<Dataset/DocumentOriginalFile>` 需先 `TableInfoHelper.initTableInfo`（testing.md:35 已记此坑） | 仅测试设施，无业务影响 |

无其他偏差：判别列方案、唯一键命名、afterCommit 模式、解析域交 Python、MQ 占位均按 TD 落地。

## 3. 测试结果

全量 `mvn test`：**BUILD SUCCESS**（0 失败 0 错误）。`check_docs_sync.py --working`、`check_ai_links.py` 均通过。

关键测试类：

| 测试类 | 用例数 | 覆盖 |
| :--- | :--- | :--- |
| `DatasetServiceImplTest`（重写） | 4 | 软删数据集/文件、不删 OSS、会话/消息物理删、notifier 载荷、回滚不通知、404 |
| `DocumentFileServiceImplTest`（重写 2 条 + 既有） | 9 | 软删文件、不删 OSS、notifier、404；同名 success/uploading→400、failed 复用保持 |
| `SoftDeleteReuseIntegrationTest`（新增，@SpringBootTest+H2） | 4 | 删后重传同名、删-传多轮不撞唯一约束（死行各带自身 id）、删后重建同名数据集、软删文件下载 404 |
| `DatasetControllerTest`（更新删除断言） | 6 | 级联软删保留 + 会话/消息物理删（库态断言） |
| `DocumentFileControllerTest`（更新删除断言 + 质量审查新增端到端重传） | 20 | 软删保留 OSS、原文件行物理保留对列表不可见、parse 表计数不变；删后经上传端点重传同名成功（列表仅见新文件、物理 2 行） |
| `ChatConversationTest`（更新） | 4 | 去 `is_deleted`/`@TableLogic` 断言 |

## 4. Scenario 覆盖（21）

- 一 隐性删除主流程（2）：DocumentFileServiceImplTest / DatasetServiceImplTest + DocumentFileControllerTest / DatasetControllerTest。
- 二 同名重建/重传（4）：SoftDeleteReuseIntegrationTest（重传成功 / 多轮不撞 / 重建数据集 / 死行+对象留存）。
- 三 同名复用共存（2）：DocumentFileServiceImplTest（success/uploading→400、failed 复用）。
- 四 会话/消息物理删（3）：DatasetServiceImplTest + DatasetControllerTest（级联物理删）；ChatControllerTest（单删回归）；ChatConversationTest（去软删）。
- 五 解析域交 Python（2）：DocumentFileServiceImplTest（不碰 parse mapper）+ DocumentFileControllerTest（parse 计数不变）+ DatasetControllerTest。
- 六 MQ 占位时机（3）：DatasetServiceImplTest（载荷含整批 ids / 回滚不通知）、DocumentFileServiceImplTest（载荷含单 id）。
- 七 不变量与边界（5）：内部下载 404（SoftDeleteReuseIntegrationTest）、归属 404（两 ServiceTest）、接口形态不变（两 ControllerTest 回归）、不删 OSS（两 ServiceTest + DocumentFileControllerTest）、仅两表软删（DatasetServiceImplTest + DatasetControllerTest）。

## 5. 发布须知

- **数据可清空、无增量迁移**：存量环境清库 / DROP 重建后套用新 `init.sql`/`schema.sql`（`CREATE TABLE IF NOT EXISTS` 不改既有表）。
- **MQ 占位未实现**：被删原文件的 parse 两表行 + Python 侧 OSS 产物本次不清理，待后续 MQ 契约需求由 Python 处理（已知分阶段缺口）。
- 删除接口对外形态不变；前端对软删无感。
- 下一步：质量审查（code-review-and-quality），通过后发 PR（branch-pr-workflow）。
