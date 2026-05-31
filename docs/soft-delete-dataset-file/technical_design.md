# 数据集/文件隐性删除（软删保留原文件）技术设计

- **文档状态：** 技术方案待审核
- **项目名称：** toLink-Service
- **业务域：** 数据集 / 文档文件（dataset / document-file）
- **需求名称：** soft-delete-dataset-file
- **业务输入：** docs/soft-delete-dataset-file/brief.md（已冻结 2026-05-30）
- **验收输入：** docs/soft-delete-dataset-file/acceptance.feature（已冻结 2026-05-30，21 Scenario）
- **输出文件：** docs/soft-delete-dataset-file/technical_design.md
- **最后更新时间：** 2026-05-30

---

## 1. 文档修订记录

| 版本号 | 修改日期 | 修改内容简述 | 来源/提出人 | 审核状态 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-30 | 初始技术设计创建 | brief.md + acceptance.feature | 待审核 |

---

## 2. 输入依据与设计目标

### 2.1 输入依据映射

| 输入来源 | 关键结论 | 技术设计承接方式 |
| :--- | :--- | :--- |
| `brief.md` | 删除改隐性删除保留原文件（不删 OSS）；判别列纳入唯一键解决同名重建/重传；会话/消息物理删并去除 `chat_conversation` 软删；解析域交 Python（Java 不删 parse 表）；MQ 纯设计预留 | §6.3 数据设计 + §7 方法级实现 + §8 组件 |
| `acceptance.feature` | 21 Scenario（隐性删除主流程/同名重建重传/会话消息物理删/解析域交 Python/MQ 占位时机/不变量边界） | §7 方法实现 + §10 测试映射，逐条覆盖 |

### 2.2 技术目标

- 删数据集 / 删文件不再物理删 OSS、不物理删原文件行；`dataset`、`document_original_file` 接入 `@TableLogic` 软删，原文件对象与 DB 行保留。
- 两表唯一键纳入「判别列 `deleted_seq`」（活行=0、软删时=自身主键 id），使软删后同名可无限次重建/重传。
- `chat_conversation` 移除 `is_deleted`/`@TableLogic`，会话与消息一律物理删；删除语义统一为「仅原文件/数据集软删保留，其余物理删」。
- 解析域（`document_parse_file` / `document_parsed_log` + Python OSS 产物）删除交 Python；Java 删除路径移除 `deleteParseRecords`、不再触碰 parse 表。
- 删除事务提交后（afterCommit）触发占位删除通知发送点（载荷含被软删 `original_file_id`），不落 producer/topic/消息体。

### 2.3 非目标

- 不实现 MQ producer / topic / 消息体 / Python 消费端（占位）；不实现回收站/恢复 UI；不做隐藏原文件冷数据 GC；不改删除/创建/上传接口对外形态；不动既有 MQ 契约与解析链路语义。

---

## 3. 改动范围

### 3.1 改动文件目录树

```text
toLink-Service/
├── link-model/src/main/java/com/qingluo/link/model/dto/entity/
│   ├── Dataset.java                    # [修改] +is_deleted(@TableLogic) +deleted_seq
│   ├── DocumentOriginalFile.java       # [修改] +is_deleted(@TableLogic) +deleted_seq
│   └── ChatConversation.java           # [修改] 移除 is_deleted 字段 + @TableLogic + @TableField
├── link-service/src/main/java/com/qingluo/link/service/
│   ├── impl/DatasetServiceImpl.java                    # [修改] delete(): 去 OSS 删 + 软删文件/数据集 + 会话/消息物理删 + 收集 id + afterCommit 占位通知
│   ├── impl/document/DocumentFileServiceImpl.java      # [修改] delete(): 去 OSS 删 + 软删文件 + 移除 deleteParseRecords + afterCommit 占位通知；删 deleteParseRecords()
│   └── delete/DocumentDeleteNotifier.java              # [新增] 占位删除通知发送点（留痕日志 + 未来 MQ 扩展点）
├── link-api/src/main/resources/schema.sql              # [修改] H2：dataset/document_original_file +软删列+判别列+新唯一键；chat_conversation 去 is_deleted + 改索引
├── docs/db/init.sql                                    # [修改] MySQL 真源：同上 + 存量迁移段
└── docs/{reference/mysql_schema.md, architecture/document_file_module.md, architecture/object_storage_module.md} # [修改] 契约/文档同步

# 测试
link-model/src/test/java/com/qingluo/link/model/entity/ChatConversationTest.java   # [修改] 移除 isDeleted/@TableLogic 相关断言
link-service/src/test/java/com/qingluo/link/service/impl/DatasetServiceImplTest.java        # [修改] 重写两条 OSS 硬删/补偿测试为软删语义 + 新增 notifier/解析域不删
link-service/src/test/java/com/qingluo/link/service/impl/DocumentFileServiceImplTest.java   # [修改] 重写两条 OSS 硬删/补偿测试为软删语义 + 移除 deleteParseRecords 期望 + notifier
link-service/src/test/java/.../SoftDeleteReuseIntegrationTest.java   # [新增] H2 集成：同名重传多轮不撞 / 删后重建数据集 / 死行+对象留存 / 内部下载 404 / 会话级联物理删
```

### 3.2 文件级改动说明

| 文件 | 动作 | 改动目的 | 是否必须 |
| :--- | :--- | :--- | :--- |
| `Dataset.java` / `DocumentOriginalFile.java` | 修改 | 加 `@TableLogic is_deleted` + `deleted_seq` 字段 | 是 |
| `ChatConversation.java` | 修改 | 移除 `is_deleted`/`@TableLogic`，会话改物理删 | 是 |
| `DatasetServiceImpl.delete()` | 修改 | 隐性删除 + 级联物理删会话/消息 + afterCommit 占位通知 | 是 |
| `DocumentFileServiceImpl.delete()` + `deleteParseRecords()` | 修改/删除 | 隐性删除 + 不再触碰 parse 表 | 是 |
| `DocumentDeleteNotifier.java` | 新增 | 占位删除通知发送点（载荷含 original_file_id） | 是 |
| `schema.sql`（H2）+ `init.sql`（MySQL） | 修改 | 软删列 + 判别列 + 唯一键迁移 + chat_conversation 去列 | 是 |
| 3 处 docs | 修改 | 契约与文档同步 | 是 |
| 3 个测试文件 + 1 新增集成测试 | 修改/新增 | 覆盖 22 Scenario，修复受语义变更影响的既有测试 | 是 |

---

## 4. 当前系统分析

| 类型 | 文件/类/方法（行） | 当前行为 | 问题或复用点 |
| :--- | :--- | :--- | :--- |
| Service | `DatasetServiceImpl.delete()`（127-167） | 循环 `ossService.deleteFile` 物理删 OSS + `evictPrivateFile`；硬删文件/会话/数据集；消息硬删 | 改造主体：去 OSS 删、文件/数据集软删、会话物理删 |
| Service | `DatasetServiceImpl.create()`（52） | `insert` + 捕获 `DataIntegrityViolationException`→400 同名 | 不改；判别列使软删后重建走通，活同名仍被捕获 |
| Service | `DocumentFileServiceImpl.delete()`（212-236） | 物理删 OSS + `deleteParseRecords` + `deleteById` | 改造主体：去 OSS 删、软删、移除 parse 清理 |
| Service | `DocumentFileServiceImpl.deleteParseRecords()`（258-269） | 硬删 `document_parse_file`/`document_parsed_log` | 删除该方法（解析域交 Python） |
| Service | `DocumentFileServiceImpl.resolveTargetRecord()`（125） | 同名分流：selectOne 命中 failed 复用、其余拦截、无则 insert | 不改；加 `@TableLogic` 后 selectOne 自动过滤死行，重传走 insert（判别列不撞），failed 复用仍作用于活行 |
| Service | `DocumentFileServiceImpl.openOriginalFile()`（242）/`getOwnedFile()`（287）/`list()`（194） | selectOne/selectList 读原文件 | 不改；`@TableLogic` 自动过滤 → 软删文件下载 404、列表隐藏 |
| Service | `ChatServiceImpl.deleteConversation()`（160） | `conversationMapper.deleteById()`（@TableLogic→软删）+ 消息硬删 | 不改代码；实体去 @TableLogic 后 `deleteById` 变物理删 |
| Service | `ChatServiceImpl.getConversations()`（63） | selectList 不引用 is_deleted，仅靠 @TableLogic 过滤 | 不改；去列后无隐藏行，行为不变 |
| 解析链路 | `DocumentParseResultServiceImpl`(53)/`DocumentParseSseServiceImpl`(43,84)/`DocumentParseTaskServiceImpl`(166,259,272) | selectById/selectOne 读 `document_original_file`/`dataset` | 加 @TableLogic 后软删行被过滤 → 删后在途解析结果回流/SSE/再投递落空（视为已删），属可接受窗口期（§12） |
| 上传回写 | `DocumentUploadStatusWriter`(58,71,90) | 守卫更新/读 uploading 记录 | 不改；操作活行，MP 自动追加 is_deleted=0 不影响 |
| 超时扫描 | `DocumentUploadStuckScanner`(36) | 扫 uploading 超时 | 不改；软删行被过滤（不会扫到已删文件，符合预期） |
| Mapper | `Dataset/DocumentOriginalFile/ChatConversation Mapper` | 均纯 `BaseMapper`，无自定义方法 | 软删用 `update(null, wrapper)`；物理删用 `delete/deleteById`（去 @TableLogic 后） |
| Controller | `DatasetController.delete`/`DocumentFileController.delete`/`ChatController.deleteConversation` | `DELETE`，返回 `Result<Void>`，`@SaCheckLogin` | 对外形态全不改 |
| 约束 | `uk_dataset_user_name`、`uk_dataset_user_name_suffix` | 唯一键，软删死行仍占名额 | 纳入判别列 `deleted_seq` 解决 |
| 先例 | `DocumentFileServiceImpl.upload()`（96-113） | `TransactionSynchronizationManager.registerSynchronization` afterCommit 投递 | 删除占位通知复用同一模式 |
| Test | `DatasetServiceImplTest`(54,73)/`DocumentFileServiceImplTest`(104,119) | 断言 OSS 删失败停库删 / 补偿异常 | 语义已变，重写为软删断言 |
| Test | `ChatConversationTest`(35,49-52,59) | 断言 isDeleted 字段存在 + @TableLogic | 字段移除，删除相关断言 |

---

## 5. 总体方案设计

### 5.1 总体流程

```mermaid
flowchart TD
    subgraph 删数据集 DatasetServiceImpl.delete
      A1["校验归属"] --> A2["selectList 名下原文件(活行) → 收集 original_file_id"]
      A2 --> A3["软删名下原文件: update set is_deleted=1, deleted_seq=id where dataset_id=? (不删 OSS)"]
      A3 --> A4["物理删名下消息 chat_message"]
      A4 --> A5["物理删名下会话 chat_conversation (实体已去 @TableLogic)"]
      A5 --> A6["软删 dataset: set is_deleted=1, deleted_seq=id"]
      A6 --> A7["注册 afterCommit: notifier.notifyAfterDelete(ids, datasetId, userId)"]
    end
    subgraph 删文件 DocumentFileServiceImpl.delete
      B1["校验归属"] --> B2["软删该原文件: set is_deleted=1, deleted_seq=id (不删 OSS, 不删 parse 表)"]
      B2 --> B3["注册 afterCommit: notifier.notifyAfterDelete([fileId], datasetId, userId)"]
    end
    subgraph 重传/重建(判别列)
      C1["selectOne 同名 (@TableLogic 过滤死行) → 视作无同名"] --> C2["insert 新活行 deleted_seq=0 → 不撞死行(deleted_seq=id)"]
    end
    A7 -.事务提交后.-> N["[占位] DocumentDeleteNotifier: 留痕日志 + TODO(future MQ)"]
    B3 -.事务提交后.-> N
```

### 5.2 模块边界

| 模块 | 职责 | 本次是否改动 |
| :--- | :--- | :--- |
| link-model | 实体软删字段/判别列；移除会话软删 | 是 |
| link-service | 两个删除链路改造；占位通知发送点 | 是 |
| link-api（resources） | H2 schema 同步 | 是 |
| docs/db | MySQL DDL 真源 + 迁移 | 是 |
| link-components-oss | 不改（仅“不再调用” deleteFile） | 否 |
| link-mapper | 不改（纯 BaseMapper，用 wrapper） | 否 |

---

## 6. API、消息与数据设计

### 6.1 API 设计

- `DELETE /api/v1/datasets/{datasetId}`、`DELETE /api/v1/files/{fileId}`、`DELETE /api/v1/chat/conversations/{id}`：**URL/入参/返回（`Result<Void>`）全不变**，前端对软删无感（删除后列表不再出现）。错误码不变：未登录 401 语义、归属/不存在 404。

### 6.2 MQ 消息设计

- **不新增/变更任何消息体或 topic**。仅预留「删除通知」发送点（`DocumentDeleteNotifier`，afterCommit 调用，载荷含 `original_file_id` 集合 + `datasetId` + `userId`），本次实现为留痕日志占位，**不**落 producer/topic/消息体、不接 Python。→ 暂不改 `mq_contracts.md`（未来 producer 落地时再加「删除通知」消息行）。

### 6.3 数据与存储设计（核心）

**(1) 判别列方案定稿：显式 UPDATE + `deleted_seq`（不采用生成列）**

- 两表各加：`is_deleted BOOLEAN NOT NULL DEFAULT FALSE`（`@TableLogic`）+ `deleted_seq BIGINT NOT NULL DEFAULT 0`。
- 规则：活行 `deleted_seq=0`；软删时 `deleted_seq=该行自身 id`（主键保证全表唯一）。
- 唯一键纳入 `deleted_seq`：
  - `dataset`：新 `uk_dataset_user_name_seq (user_id, name, deleted_seq)`，删旧 `uk_dataset_user_name`。
  - `document_original_file`：新 `uk_dof_name_suffix_seq (dataset_id, user_id, original_filename, file_suffix, deleted_seq)`，删旧 `uk_dataset_user_name_suffix`。
- 不变量证明：活行皆 `deleted_seq=0` → 同名活行相撞（互斥保持）；死行各自 `deleted_seq=id` 互不相同（无限轮删/传不撞）；活(0) vs 死(id≥1) 永不相同（死行不挡活行）。
- **为何不用生成列（活=0/删=NULL，靠 NULL 唯一性）**：需同时依赖「STORED 生成列」+「唯一索引中 NULL 互不相等」两项 DB 特性在 H2(MySQL 模式) 与 MySQL 8 双端一致，且 init.sql/schema.sql 两份 DDL 易漂移；显式 UPDATE 方案零特性依赖、可控、跨库一致，故定稿显式 UPDATE。
- **@TableLogic 协作**：`@TableLogic` 仅用于读自动过滤；删除不走自动 `deleteById`/`delete`（只写 is_deleted），改显式 `update(null, wrapper.set(is_deleted,true).set/​setSql(deleted_seq))`。MP 对 wrapper update 自动追加 `AND is_deleted=0`（幂等：重复删空操作；只软删活行）。
  - 单行删（已知 id）：`.set(deleted_seq, record.getId())`。
  - 级联删（按 dataset_id 批量）：`.setSql("deleted_seq = id")`（引用各行自身 id，H2/MySQL 均支持）。

**(2) chat_conversation 去软删**

- 移除列 `is_deleted`；索引 `idx_chat_conversation_user_active_list` 由 `(user_id, is_deleted, is_pinned, updated_at)` 改为 `(user_id, is_pinned, updated_at)`。
- `deleteConversation` 的 `deleteById` 与数据集级联 `chatConversationMapper.delete(...)` 去 @TableLogic 后即物理删。

**(3) 存量数据可清空，无增量迁移（已确认）**

- 当前为开发/预发阶段，相关表无需保留的生产数据，**不做增量迁移**——直接以新结构建表：清库 / `DROP TABLE` 重建后套用新 `init.sql`（MySQL 真源）与 `schema.sql`（H2）。
- 因此**无需** chat_conversation「先清 `is_deleted=1` 行再 drop 列」、也无需唯一键「先建后删」的零空窗编排——直接按新结构建表即可。
- 软删运行设计不受影响：以后新发生的删除仍是软删（`is_deleted` + `deleted_seq`）。"数据可清空" 只针对一次性存量，不改变运行语义。
- 若日后有需保留的生产数据，再单独设计增量迁移（不在本次范围，§11）。

---

## 7. 方法级实现方案

### 7.1 方法级变更总表

| 文件 | 类/对象 | 方法/成员 | 动作 | 入参变化 | 返回变化 | 改动目的 | 对应 Scenario |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Dataset | Dataset | `isDeleted`/`deletedSeq` | 新增 | - | - | 软删 + 判别列 | 一/二/七 |
| DocumentOriginalFile | DocumentOriginalFile | `isDeleted`/`deletedSeq` | 新增 | - | - | 软删 + 判别列 | 一/二/七 |
| ChatConversation | ChatConversation | `isDeleted` + @TableLogic | 删除 | - | - | 会话改物理删 | 四 |
| DatasetServiceImpl | DatasetServiceImpl | `delete()` | 修改 | 不变 | 不变 | 去 OSS 删 + 软删 + 会话/消息物理删 + 占位通知 | 一(2)/四(级联)/五/六/七 |
| DocumentFileServiceImpl | DocumentFileServiceImpl | `delete()` | 修改 | 不变 | 不变 | 去 OSS 删 + 软删 + 不删 parse + 占位通知 | 一(1)/五/六/七 |
| DocumentFileServiceImpl | DocumentFileServiceImpl | `deleteParseRecords()` | 删除 | - | - | 解析域交 Python | 五 |
| DocumentFileServiceImpl | DocumentFileServiceImpl | `resolveTargetRecord()` | 不改 | - | - | 重传走 insert（判别列）+ failed 复用共存 | 二/三 |
| DocumentFileServiceImpl | DocumentFileServiceImpl | `openOriginalFile()` | 不改 | - | - | 软删文件 @TableLogic 过滤 → 404 | 七 |
| ChatServiceImpl | ChatServiceImpl | `deleteConversation()` | 不改 | - | - | 实体去 @TableLogic 后 deleteById 物理删 | 四 |
| DocumentDeleteNotifier | DocumentDeleteNotifier | `notifyAfterDelete(ids,datasetId,userId)` | 新增 | - | void | 占位删除通知发送点（留痕日志） | 六 |

### 7.2 逐方法实现设计

#### 7.2.1 `DatasetServiceImpl.delete()`（修改，@Transactional）

- 修改后职责：隐性删除数据集——软删名下原文件与数据集行（不删 OSS），物理删名下会话/消息，提交后触发占位通知。
- 详细步骤（事务内）：
  1. `Dataset dataset = getOwnedDataset(userId, datasetId)`（不存在/越权 → 404）。
  2. `List<DocumentOriginalFile> files = documentOriginalFileMapper.selectList(eq(datasetId))`（@TableLogic 自动只返回活行）；`List<Long> fileIds = files.stream().map(::getId)`。
  3. **软删名下原文件**：`documentOriginalFileMapper.update(null, new LambdaUpdateWrapper<DocumentOriginalFile>().eq(datasetId).set(isDeleted,true).setSql("deleted_seq = id"))`（移除原 132-147 的 OSS 删除循环与 evict）。
  4. **物理删消息**：保留按会话 id 删 `chat_message` 的逻辑（`chatMessageMapper.delete(eq(conversationId))`）。
  5. **物理删会话**：`chatConversationMapper.delete(eq(datasetId))`（实体去 @TableLogic 后为物理删）。
  6. **软删数据集**：`datasetMapper.update(null, new LambdaUpdateWrapper<Dataset>().eq(id, dataset.getId()).set(isDeleted,true).set(deletedSeq, dataset.getId()))`（替换原 `deleteById`）。
  7. `registerAfterCommit(() -> notifier.notifyAfterDelete(fileIds, datasetId, userId))`（见 7.2.4）。
- 事务与异常边界：全程单事务；任一 DB 操作异常 → 抛出 → 事务回滚 → afterCommit 不执行（占位通知不发）。移除原“OSS 已删但 DB 删失败”的补偿语义（不再有 OSS 物理删）。
- 不触碰 `document_parse_file`/`document_parsed_log`（解析域交 Python）。
- 对应测试：§10（S 一(2)/四级联/五/六/七）。

#### 7.2.2 `DocumentFileServiceImpl.delete()`（修改，@Transactional）

- 修改后职责：隐性删除单个文件——软删原文件行（不删 OSS、不删 parse 表），提交后触发占位通知。
- 详细步骤（事务内）：
  1. `DocumentOriginalFile record = getOwnedFile(userId, fileId)`（不存在/越权/已软删 → 404）。
  2. **软删原文件**：`documentOriginalFileMapper.update(null, new LambdaUpdateWrapper<DocumentOriginalFile>().eq(id, record.getId()).set(isDeleted,true).set(deletedSeq, record.getId()))`（移除原 214-227 OSS 删 + evict；移除 229 `deleteParseRecords`；替换 230 `deleteById`）。
  3. `registerAfterCommit(() -> notifier.notifyAfterDelete(List.of(record.getId()), record.getDatasetId(), userId))`。
- 异常边界：单事务，异常回滚 → 占位通知不发。移除补偿语义。
- 对应测试：§10（S 一(1)/五/六/七）。

#### 7.2.3 `DocumentFileServiceImpl.deleteParseRecords()`（删除）

- 删除该方法及其调用；`document_parse_file`/`document_parsed_log` 不再由 Java 删除（解析域交 Python）。S 五。

#### 7.2.4 afterCommit 占位通知（两处删除复用同一模式）

- 复用上传链路模式：
  ```
  if (TransactionSynchronizationManager.isActualTransactionActive()
      && TransactionSynchronizationManager.isSynchronizationActive()) {
      registerSynchronization(new TransactionSynchronization() {
          public void afterCommit() { notifier.notifyAfterDelete(ids, datasetId, userId); }
      });
  } else {
      notifier.notifyAfterDelete(ids, datasetId, userId); // 无事务（如单测）直接调用
  }
  ```
- 调用点位于各 delete() 的 DB 软删/物理删之后；DB 操作抛异常则不会到达此处（回滚不通知）。S 六（含回滚不触发）。

#### 7.2.5 `DocumentDeleteNotifier`（新增，link-service `service/delete/`）

- 单一 `@Component`，方法 `void notifyAfterDelete(Collection<Long> originalFileIds, Long datasetId, Long userId)`。
- 本次实现：`log.info` 留痕（含 ids/datasetId/userId）+ 代码注释 TODO「future: 投递 tolink.rag.* 删除通知，仿 DocumentParseTaskMQ」。**不**注入 MQ producer。
- 为可测的「发送点」seam：被两处 delete 在 afterCommit/无事务时调用一次。S 六。

#### 7.2.6 实体改动

- `Dataset` / `DocumentOriginalFile`：新增 `@TableLogic @TableField("is_deleted") private Boolean isDeleted = false;` 与 `@TableField("deleted_seq") private Long deletedSeq = 0L;`（沿用 `ChatConversation` 既有 `@TableLogic` 写法）。
- `ChatConversation`：删除 `isDeleted` 字段及其 `@TableLogic`/`@TableField("is_deleted")`、`@Schema` 注解与 import。

#### 7.2.7 不改但需复核的读路径（`@TableLogic` 自动过滤生效点）

- 列表/详情/归属/同名校验/内部下载（`DatasetServiceImpl.list/getOwnedDataset`、`DocumentFileServiceImpl.list/getOwnedFile/resolveTargetRecord/openOriginalFile`、`ChatServiceImpl` 数据集归属）→ 软删行自动隐藏（S 一/七）。
- 解析链路读 `document_original_file`/`dataset`（`DocumentParseResult/Sse/TaskServiceImpl`）→ 软删行被过滤，删后在途解析读原文件落空（视为已删），属可接受窗口期（§12）。

---

## 8. 组件与集成设计

- **DocumentDeleteNotifier**：注入 `DatasetServiceImpl`、`DocumentFileServiceImpl`。本次为占位（日志），未来在其内落地 MQ producer（topic/消息体单独立项），调用方与时机（afterCommit）不变。
- **contract-guard 视角**：本次仅 schema 变更（软删列/判别列/唯一键/去 chat_conversation 列）属高风险，须同步 `mysql_schema.md`；删除接口对外契约不变；MQ 仅占位不动 `mq_contracts.md`；OSS 仅“不再调用 deleteFile”，删除策略约定同步 `object_storage_module.md`。

---

## 9. 异常处理与降级策略

| 异常场景 | 处理方式 | 是否抛出 | 是否影响接口/通知 |
| :--- | :--- | :--- | :--- |
| 删除目标不存在/越权 | getOwned* 抛 404 | 是 | 接口 404，无任何删除、不通知 |
| 软删/物理删 DB 异常 | 异常传播，事务回滚 | 是 | 接口 5xx，数据回滚，afterCommit 不触发（不通知） |
| afterCommit 通知占位异常 | notifier 内吞异常仅日志（占位不应影响已提交删除） | 否 | 删除已提交，仅日志 |
| 删后在途解析读软删文件落空 | 解析链路按“文件不存在”处理 | 否 | 可接受窗口期，不回退删除 |

---

## 10. 测试方案

### 10.1 方法级测试映射

| 被测 | 测试文件 | 对应 Scenario | 断言要点 |
| :--- | :--- | :--- | :--- |
| `DocumentFileServiceImpl.delete` 软删 | `DocumentFileServiceImplTest`（Mockito，重写） | 一(1)/五/七 | 未调 `ossService.deleteFile`/`evictPrivateFile`；发出软删 update（is_deleted=1, deleted_seq=id）；未调 parse 两表 mapper（无 deleteParseRecords）；notifier 被调用一次（[fileId]） |
| `DatasetServiceImpl.delete` 软删+级联 | `DatasetServiceImplTest`（Mockito，重写） | 一(2)/四/五/六/七 | 未调 OSS 删；文件/数据集发软删 update；`chatConversationMapper.delete`+`chatMessageMapper.delete` 被调用（物理删）；未调 parse 两表；notifier 被调用一次（ids={F1,F2}） |
| 回滚不通知 | `DatasetServiceImplTest`（软删 update 抛异常） | 六 | 抛异常；notifier 从未被调用 |
| 同名重传多轮不撞 | `SoftDeleteReuseIntegrationTest`（@SpringBootTest+H2） | 二 | 删→传 循环 4 轮全成功、无 `DataIntegrityViolation`；存在 1 活行 + N 死行；死行 deleted_seq 互异 |
| 删后重建同名数据集 | `SoftDeleteReuseIntegrationTest` | 二 | create→delete→create 同名成功；旧死行保留 |
| 死行+对象留存 | `SoftDeleteReuseIntegrationTest` | 二 | 旧死行保留 objectKey=K1；本地 OSS 对象 K1 仍存在；新行 K2≠K1 |
| 同名命中活记录 | `DocumentFileServiceImplTest` | 三 | success/uploading→400；failed→复用同一行（无新 insert）；死行不参与 |
| 软删文件内部下载 404 | `DocumentFileServiceImplTest`/集成 | 七 | openOriginalFile 对软删文件 → 404（@TableLogic 过滤） |
| 列表/详情隐藏软删 | `SoftDeleteReuseIntegrationTest` | 一/七 | 软删后 list/detail 不返回；DB 行物理仍在 |
| 会话级联/单删物理删 | `SoftDeleteReuseIntegrationTest` + `ChatControllerTest`（既有 delete 用例） | 四 | 删数据集/删会话后 chat_conversation/chat_message 行物理消失 |
| 会话列表行为不变 | `ChatControllerTest`（既有 getConversations 用例，回归） | 四 | 列表按置顶/更新时间返回，去列后绿 |
| 实体软删/会话去软删 | `ChatConversationTest`（修改） | 四 | 移除 isDeleted/@TableLogic 断言后绿 |
| 归属/接口形态 | `DatasetControllerTest`/`DocumentFileControllerTest`（既有，回归） | 七 | 删除 404 与成功 `Result<Void>` 形态不变 |

### 10.2 Scenario 覆盖自检

| Scenario | 承接方法 | 承接测试 | 覆盖 |
| :--- | :--- | :--- | :--- |
| 删单文件软删保留原文件 | DocumentFileServiceImpl.delete | ServiceTest + IntegrationTest | ✅ |
| 删数据集级联软删保留 | DatasetServiceImpl.delete | ServiceTest + IntegrationTest | ✅ |
| 删文件后重传同名成功 | resolveTargetRecord + 判别列 | IntegrationTest | ✅ |
| 多轮删传不冲突 | 判别列 deleted_seq | IntegrationTest | ✅ |
| 删数据集后重建同名 | create + 判别列 | IntegrationTest | ✅ |
| 死行+OSS 对象留存 | delete 软删（不删 OSS） | IntegrationTest | ✅ |
| 活 success/uploading→400(Outline) | resolveTargetRecord | ServiceTest | ✅ |
| 活 failed 复用，死行不参与 | resolveTargetRecord | ServiceTest（+集成造死行） | ✅ |
| 删数据集级联物理删会话+消息 | DatasetServiceImpl.delete | ServiceTest + IntegrationTest | ✅ |
| 单删会话物理删 | deleteConversation（实体改） | IntegrationTest/ChatControllerTest | ✅ |
| 去 is_deleted 后列表不变 | getConversations | ChatControllerTest 回归 | ✅ |
| 删文件后不删 parse 两表 | delete（移除 deleteParseRecords） | ServiceTest | ✅ |
| 删数据集后不删 parse 两表 | delete | ServiceTest | ✅ |
| 删文件 afterCommit 触发发送点 | delete + notifier | ServiceTest | ✅ |
| 删数据集载荷含整批 ids | delete + notifier | ServiceTest | ✅ |
| 事务回滚不触发发送点 | delete afterCommit | ServiceTest（抛异常） | ✅ |
| 软删文件内部下载 404 | openOriginalFile | ServiceTest/集成 | ✅ |
| 删除目标不存在/越权 404(Outline) | getOwned* | ServiceTest/ControllerTest | ✅ |
| 删除接口对外形态不变 | controller delete | ControllerTest 回归 | ✅ |
| 删除不物理删 OSS（不变量） | 两 delete | ServiceTest（never ossService.deleteFile） | ✅ |
| 仅两表软删（不变量） | 两 delete | ServiceTest（软删 update vs 物理 delete vs 不碰 parse） | ✅ |

### 10.3 回归命令

```bash
mvn -pl link-model test
mvn -pl link-service test
mvn -pl link-api test
mvn test
python3 scripts/check_docs_sync.py --working
python3 scripts/check_ai_links.py
```

---

## 11. 发布与回滚

- **存量数据可清空（已确认）**：开发/预发阶段无需保留的生产数据，**不做增量迁移**——直接以新结构建表（清库 / `DROP TABLE` 重建）。
- **发布**：同步 `docs/db/init.sql`（MySQL 真源）+ `link-api/src/main/resources/schema.sql`（H2）为新结构（软删列 + 判别列 + 新唯一键；`chat_conversation` 去 `is_deleted` + 改索引）。注意 `CREATE TABLE IF NOT EXISTS` 不会改既有表，存量环境需 DROP 重建或手工对齐。
- **回滚**：代码回滚 + schema 回退即可（数据可丢弃，无软删存量需照顾）。
- **MQ 占位**：无 producer，无需协调 Python；未来 MQ 落地再行联调。
- **未来生产化**：若日后有需保留的生产数据，再单独设计增量迁移（chat_conversation 去列先清行、唯一键先建后删等），不在本次范围。

---

## 12. 风险与待确认问题

| 风险/问题 | 影响 | 建议处理 |
| :--- | :--- | :--- |
| 解析链路读软删原文件落空 | 删后在途解析结果回流/SSE/再投递视为“文件不存在” | 可接受窗口期（文件已删）；与 brief §4 一致；如需更稳，未来扫描/消费侧加“软删跳过”显式分支 |
| parse 两表 + Python OSS 产物滞留 | 本次无人清理（MQ 未落地） | 已知分阶段缺口；惰性数据；后续 MQ 需求由 Python 清理 |
| 待确认：判别列名 `deleted_seq`、新唯一键名 | 命名一致性 | 暂定 `deleted_seq` / `uk_dataset_user_name_seq` / `uk_dof_name_suffix_seq`，评审确认 |

---

## 13. 实施顺序

1. link-model：`Dataset`/`DocumentOriginalFile` 加 `is_deleted`+`deleted_seq`；`ChatConversation` 去 `is_deleted`。
2. DB：`schema.sql`（H2）+ `init.sql`（MySQL）同步为新结构与唯一键/索引（数据可清空重建，无增量迁移）。
3. link-service：`DocumentDeleteNotifier` → `DatasetServiceImpl.delete` 重构 → `DocumentFileServiceImpl.delete` 重构 + 删 `deleteParseRecords`。
4. 测试：重写 `DatasetServiceImplTest`/`DocumentFileServiceImplTest` 两条 OSS/补偿用例为软删语义；改 `ChatConversationTest`；新增 `SoftDeleteReuseIntegrationTest`（H2）覆盖二/四/七关键场景。
5. 文档同步（mysql_schema / document_file_module / object_storage_module）+ `check_docs_sync.py` + `check_ai_links.py`。
6. 全量 `mvn test`。

---

## 14. 人工审核清单

- [ ] 改动文件目录树已确认
- [ ] 方法级变更总表已确认（两 delete 重构、deleteParseRecords 删除、实体软删/去会话软删、DocumentDeleteNotifier）
- [ ] 数据/事务边界已确认（判别列显式 UPDATE、唯一键迁移顺序、chat_conversation 去列先清行、afterCommit 占位、回滚不通知）
- [ ] 测试方案已确认（22 Scenario 全覆盖；迁移不复活的承接方式已知会）
- [ ] DDL 双源同步（init.sql + schema.sql）与 mysql_schema 文档同步已确认
