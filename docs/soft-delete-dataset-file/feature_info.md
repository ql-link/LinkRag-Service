# feature_info

| 项 | 值 |
| :--- | :--- |
| 需求名 | soft-delete-dataset-file |
| 中文名 | 数据集/文件删除改为隐性删除（软删保留原文件 + 预留 MQ 通知 Python 删产物） |
| 来源 | GitHub issue ql-link/LinkRag-Service#27（取代 #1 的 P2「并行硬删 OSS」设想） |
| 分支 | feature/soft-delete-dataset-file（待创建） |
| 当前阶段 | 质量审查 APPROVE（2026-05-30），待提交/PR |
| brief.md | 已冻结（2026-05-30） |
| acceptance.feature | 已冻结（2026-05-30，21 Scenario；原 22，移除迁移不复活场景） |
| technical_design.md | 已审核（2026-05-30） |
| implementation_report.md | 已生成（2026-05-30） |

## 范围决策（来自 issue #27 + 用户澄清）

| 项 | 本需求处置 |
| :--- | :--- |
| 数据集删除：物理删 OSS + 删 DB 行 | 改为**软删保留**：不删 OSS、原文件行软删、数据集行软删 |
| 文件删除：物理删 OSS + 删 DB 行 | 改为**软删保留**：不删 OSS、原文件行软删 |
| `dataset` / `document_original_file` 软删字段 | **本次范围**：接入 `@TableLogic`（复用 `chat_conversation` 先例） |
| 唯一键冲突（软删后同名重建/重传） | **本次范围**：唯一键纳入「判别列」（活=0/删=自身 id）；两表同构（含 issue 漏掉的 `dataset`） |
| `chat_conversation` / `chat_message` | **一律物理硬删**；**移除 `chat_conversation` 的 `is_deleted`/`@TableLogic`**（单删会话与级联删都成物理删，无需绕过逻辑删）；`chat_message` 本就无软删字段。迁移：先清 `is_deleted=1` 行再 drop 列并改索引 |
| `document_parse_file` / `document_parsed_log` | **交 Python 删**（随 MQ 清理）；Java 删除路径**移除 `deleteParseRecords`**、本次起不触碰 parse 表 |
| OSS 原文件对象 | **保留**（不物理删） |
| Python 侧 OSS 产物（清洗文件/向量） | **交 Python 删**（与 parse 两表打包，随删除通知一起删） |
| MQ 通知 Python 删衍生产物 | **占位/纯设计预留**：预留 afterCommit 发送点 + 载荷要点；不落 producer/topic/消息体、不实现 Python 侧 |
| Python 侧产物删除实现 | 不做（单独立项） |
| 隐藏原文件冷数据物理清理（GC） | 不做（后续评估） |
| 用户可见的回收站/恢复功能 | 不做（软删仅为后端追溯/未来恢复铺垫） |
| 删除/创建/上传接口对外形态 | 不变（前端对软删无感） |
| 缓存补偿 / MQ 投递重试（#1 其余项） | 不做 |

## acceptance 覆盖

| 分类 | Scenario 数 |
| :--- | :--- |
| 一、隐性删除主流程（软删保留原文件、不删 OSS） | 2 |
| 二、删后同名重建/重传（判别列：重传/重建成功、多轮不撞、死行+OSS 留存） | 4 |
| 三、与既有同名复用逻辑共存（活 success/uploading→400、活 failed 复用） | 2 |
| 四、会话/消息物理删 + 去 chat_conversation 软删（级联/单删/列表不变） | 3 |
| 五、解析域交 Python（删文件/删数据集后 parse 两表行留存） | 2 |
| 六、通知 Python 占位时机（afterCommit 发送点/载荷含 original_file_id/回滚不触发） | 3 |
| 七、不变量与边界（内部下载 404 / 归属 404 / 接口形态不变 / 不删 OSS / 仅两表软删） | 5 |
| 合计（含 2 个 Scenario Outline） | 21 |

## 阶段记录

- 2026-05-30 issue 评估：对照真实代码核实 #27 的现状描述。确认两个删除入口（`DatasetServiceImpl.delete` 约 127–167、`DocumentFileServiceImpl.delete` 约 212）确为物理硬删 OSS + 删 DB 行；`chat_conversation` 已是 `@TableLogic` 软删先例；`dataset` 有 `uk_dataset_user_name`、`document_original_file` 有 `uk_dataset_user_name_suffix`，两表当前都无软删字段。**纠正 issue 一处遗漏**：issue 只点了文件表唯一键，漏了 `dataset` 同名唯一键，需一并处理。
- 2026-05-30 需求澄清（用户确认 4 项关键决策）：①隐性删除、只保留原文件（不删 OSS、原文件软删）；②`dataset` 也软删（保留空壳便于整集恢复）；③删数据集时 `chat_conversation` + `chat_message` 直接硬删，不保留；④解析派生行 Java 侧继续硬删；⑤MQ 通知 Python 仅占位。提出并论证唯一键「判别列」方案（布尔维度会在第二次删除二次相撞，需用每次删除唯一的判别值如主键 id）。
- 2026-05-30 brief 首版：范围定为「原文件软删保留 + 唯一键判别列 + 衍生硬删 + MQ 占位」。§0 核实现状前提；§2 给出删数据集/删文件/删后重建三张流程图 + 各表删除语义矩阵；§3 拆软删字段与判别列、两个删除链路改造、读路径与同名兼容、MQ 占位、文档同步；§4 列风险表。§5 留 4 个待确认。
- 2026-05-30 §5 待确认敲定（用户决策）：①唯一键采用**判别列**方案（活=0/删=自身 id；已论证布尔维度会二次相撞）；②**解析派生行（`document_parse_file` / `document_parsed_log`）改为交 Python 删**——核实 `document_parsed_log` Java 全程不写（纯 Python 写）、`document_parse_file` Java 建壳 Python 维护，两表与 Python OSS 产物耦合，整体归 Python 随 MQ 清理更一致；据此 Java 删除路径**移除 `deleteParseRecords`**，并明确「Java 本次不删 parse 表」的已知滞留缺口（惰性数据，随后续 MQ 由 Python 清理）；③MQ **纯设计预留**（不落 producer）；④命名沿用 `soft-delete-dataset-file`。brief 同步重写 §1/§2/§3/§4/§5 与删除语义矩阵。
- 2026-05-30 会话域决策（用户提出）：**移除 `chat_conversation` 的 `is_deleted`/`@TableLogic`**——核实其 `deleteConversation` 现为「会话软删 + 消息硬删」的半软半硬、且消息已硬删使会话软删无真实可恢复性；`getConversations`/`getOwnedConversation` 仅靠 `@TableLogic` 自动过滤、不显式引用 `is_deleted`。移除后单删会话与数据集级联删都成物理硬删，**消除了原计划的「绕过 @TableLogic」复杂度**；`chat_message` 本就无软删字段。新增迁移要点：**先物理删 `is_deleted=1` 存量行 → 再 drop 列 → 再改索引 `idx_chat_conversation_user_active_list`（去 `is_deleted`）**，否则隐藏行复活。删除语义统一为「只有原文件/数据集软删保留，其余物理删」。brief §0/§1/§2/§3.4(新增)/§4/§5 同步。§5 无阻塞项，待用户确认 brief 冻结。
- 2026-05-30 **brief 冻结**：用户确认冻结，§5 无遗留待确认项。决策全集（隐性删除保留原文件、判别列唯一键、会话/消息物理删并去除 `chat_conversation` 软删、解析域交 Python、MQ 纯设计预留）已固化。进入 acceptance 阶段（acceptance-generator）。
- 2026-05-30 acceptance 首版：基于冻结 brief 生成 22 个 Scenario（含 2 个 Scenario Outline），七大分类全覆盖已冻结决策。重点把「同名重传多轮不撞唯一约束（判别列）」「会话/消息物理删与去 `chat_conversation` 软删（含迁移不复活）」「解析两表行删后仍留存（交 Python）」「afterCommit 占位发送点载荷含 `original_file_id`、回滚不触发」「删除不调 OSS 物理删、仅两表软删」等落为可断言 Then。断言遵循可机器验证原则，不写判别列列名/取值等实现细节。待用户评审冻结。
- 2026-05-30 **acceptance 冻结**：22 个 Scenario（含 2 Scenario Outline）评审通过，与全部冻结决策对齐。进入 technical_design 阶段（technical-design）。
- 2026-05-30 technical_design 草稿：基于真实代码核实后定方案——判别列定稿**显式 UPDATE + `deleted_seq`**（活=0/删=自身 id，纳入新唯一键 `uk_dataset_user_name_seq`/`uk_dof_name_suffix_seq`），论证不采用生成列（避免 H2/MySQL 生成列+NULL 唯一性双端漂移）；两 delete 重构（去 OSS 删、显式软删 update、级联 setSql("deleted_seq = id")、会话/消息物理删）；删 `deleteParseRecords`（解析域交 Python）；新增 `DocumentDeleteNotifier` 占位发送点（afterCommit，载荷含 original_file_id）；`ChatConversation` 去 `is_deleted`/@TableLogic + 索引去列 + 迁移先清 is_deleted=1 行。核实 8 个 mapper 读写点确认 @TableLogic 自动过滤覆盖（含解析链路窗口期）。22 Scenario 全映射（「迁移不复活」标注为迁移脚本级承接）。识别将重写的既有测试：`DatasetServiceImplTest`/`DocumentFileServiceImplTest` 各 2 条 OSS 硬删/补偿用例、`ChatConversationTest` 的 isDeleted/@TableLogic 断言。待 TD 审核。
- 2026-05-30 范围简化（用户决策）：**现阶段表数据可直接清空，无需存量迁移**。据此：acceptance 移除「迁移不复活历史已删会话」场景（22→21，§四 4→3）；TD §6.3(3)「存量迁移」改为「数据可清空、无增量迁移、直接重建」、§11 发布回滚简化（无有序 DDL 迁移、无软删存量回滚顾虑）、§12 移除 3 条迁移/回滚风险、§13 step2 去「含存量迁移段」、§10.2 移除迁移场景行与 ⚠️ 标注。软删运行设计不变（仅一次性存量处理简化）。待 TD 审核。
- 2026-05-30 **TD 审核通过**：判别列显式 UPDATE 方案、两 delete 重构、去 chat_conversation 软删、解析域交 Python、MQ 占位、数据可清空无迁移等全部确认。进入实现阶段（implementation-execution），按 TD §13 顺序落地。
- 2026-05-30 **实现完成**：按 §13 落地（实体软删+判别列/去会话软删 → DDL 双源 → DocumentDeleteNotifier → 两 delete 重构+删 deleteParseRecords → 测试 → 文档同步）。新增 `SoftDeleteReuseIntegrationTest`（H2，验证判别列同名重传多轮不撞）；重写 `DatasetServiceImplTest`/`DocumentFileServiceImplTest` 各 2 条 OSS 用例 + 改 `ChatConversationTest`；**额外修复** TD 未列出的 `DatasetControllerTest`/`DocumentFileControllerTest` 删除断言（原断言旧硬删/删 OSS 语义）。全量 `mvn test` BUILD SUCCESS；check_docs_sync/check_ai_links 通过。文档同步 5 篇。偏差 2 处见 implementation_report.md。已生成 implementation_report.md，进入质量审查（code-review-and-quality）。发布前置：MQ 占位未实现（Python 侧产物清理待后续）。
- 2026-05-30 **质量审查 APPROVE**：6 维度（Correctness/Tests/Architecture/Security/Performance/Contracts）通过，0 Critical、0 Required。1 个 Suggestion 已就地修复——补端到端测试 `DocumentFileControllerTest.Should_AllowReuploadSameName_AfterSoftDelete`（删后经上传端点重传同名成功，闭合“同名重传”头部场景的 endpoint 级覆盖）；其余建议记录待后续：①级联软删 deleted_seq=id 值未直接断言（SQL 构造正确、单文件路径已断言，低优先）；②H2/MySQL 的 chat_conversation 列表索引命名分歧（既有、非本次引入）；③数据集删除按会话逐条删消息的 N+1（既有）。全量 `mvn test` BUILD SUCCESS、doc-sync/ai-links 通过。待提交/PR（branch-pr-workflow）。
