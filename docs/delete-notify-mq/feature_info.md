# feature_info

| 项 | 值 |
| :--- | :--- |
| 需求名 | delete-notify-mq |
| 中文名 | 删除链路（续）：MQ 删除通知 Java producer 落地 + 契约定义 |
| 来源 | GitHub issue ql-link/LinkRag-Service#29（承接 #27 / PR #28 占位） |
| 分支 | feature/delete-notify-mq（已推送，commit 0147f86） |
| 当前阶段 | 已发 PR #33 → dev（2026-05-30），待评审/合并 |
| brief.md | 已冻结（2026-05-30），无遗留待确认项 |
| acceptance.feature | 已冻结（2026-05-30，9 Scenario 含 5 Scenario Outline） |
| technical_design.md | 已审核（2026-05-30），发布前置已决策（队列积压→上线协调） |
| implementation_report.md | 已生成（2026-05-30） |

## acceptance 覆盖

| 分类 | Scenario 数 |
| :--- | :--- |
| 一、删除通知投递主流程（file/dataset 分流、多文件仍一条 dataset 通知） | 3 |
| 二、消息契约形态（扁平 JSON snake_case / 点对点队列 / 最简载荷无 trace 字段） | 1（Outline） |
| 三、afterCommit 时机（回滚不发、删除未生效） | 1（Outline） |
| 四、发送失败兜底（投递抛异常时删除仍成功、异常不外抛） | 1（Outline） |
| 五、发送前完整性校验（delete_type/dataset_id/user_id/file 范围 original_file_id 缺失则拒发） | 1（Outline） |
| 六、边界（删除未发生不投递、删空数据集仍投递 dataset 通知） | 2 |
| 合计（含 5 个 Scenario Outline） | 9 |

## 范围决策（来自 issue #29 + 用户澄清）

| 项 | 本需求处置 |
| :--- | :--- |
| 删除通知 Java producer | **本次范围**：把 `DocumentDeleteNotifier` 占位升级为真实 `MQSend` 投递（afterCommit 时机复用前序） |
| 删除通知消息契约（topic + 消息体） | **本次范围**：新增实现 `AbstractMQ` 的消息，topic 建议 `tolink.rag.document_delete`、`QUEUE`、扁平 JSON snake_case；载荷 `original_file_id` 列表 + `dataset_id` + `user_id`（仿 `DocumentParseTaskMQ`） |
| 可靠性口径 | **接受偶尔漏发**：尽力发 + 失败告警/留痕、notifier 内吞掉不影响已提交删除；**无 DLQ、不做 transactional outbox**（用户确认） |
| 幂等 | 按 `original_file_id` 删衍生产物天然幂等（删第二次 no-op）；**Java 侧无需额外去重字段**（用户确认） |
| 消息粒度（用户决策，更新） | **删数据集传 `dataset_id`、删文件传 `original_file_id`**（按删除范围分流），从源头避免长列表撑爆消息体；不再下发整批 file id。Python 按 `dataset_id` / `original_file_id` 删 |
| trace id / 去重字段 | **不加**（用户确认）：幂等天然成立，载荷保持最简（`delete_type` + `dataset_id` + `user_id` + 文件范围 `original_file_id`） |
| 漏发观测兜底（对账扫描） | **不做**（用户确认）：已接受漏发；如需后续单独立项 |
| 队列选型 | 跟 `parse_task` 一致走 RabbitMQ（用户确认）；队列由 `RabbitMQTopologyScanner` 自动声明 |
| afterCommit 调用时机 + 载荷收集 | **不改**：前序 #28 已在两个删除入口就位，本次仅复用 |
| 契约文档同步 | **本次范围**：`mq_contracts.md` + `mq_module.md` + `integration.md`（删除链路改「已落地」） |
| Python 侧消费 + 真删衍生产物 | **不做**（另一仓库 / issue 第 2 部分）；本 brief 只定契约作为其实现依据 |
| 端到端联调（issue 第 3 部分） | **不做**：依赖 Python 侧就绪后另行联调；本仓库验收到「通知按契约正确投递」为止 |
| 隐藏原文件冷数据 GC / 回收站恢复 | 不做（后续另议） |
| Java 软删本身 | 不做（#27 / PR #28 已完成） |

## 阶段记录

- 2026-05-30 issue 评估：拉取 issue #29 及其上下文（父 issue #27、已合并 PR #28、Java 侧占位代码）。核实仓库 `ql-link/LinkRag-Service` 即本 Java 管理端代码；issue 第 1 部分（MQ 通知 Java 半）落在本仓库，第 2 部分（Python 消费）在另一代码库。核实占位 `DocumentDeleteNotifier`（仅留痕未投递）、两个删除入口 afterCommit 调用已就位、既有 `DocumentParseTaskMQ`/`AbstractMQ`/`MQSend` 风格、RabbitMQ 拓扑自动声明、parse_result 消费兜底为消费侧（与本需求生产侧不同）。
- 2026-05-30 需求澄清（用户确认关键取舍）：①可靠性走轻量口径——**接受偶尔漏发**，尽力发 + 失败告警、无 DLQ、不做 outbox；②先写 brief 进入 Spec-as-Test 流程。
- 2026-05-30 brief 首版（草稿）：范围定为「删除通知 Java producer 落地 + 契约定义 + 文档同步」，明确 Python 消费与端到端联调不在本仓库。§5 留 4 个待确认（命名 / 是否加 trace id / 超大列表是否分批 / 是否补漏发观测兜底）。
- 2026-05-30 用户决策（4 项，brief 同步重写）：①**不加 trace id**，载荷保持最简；②**漏发观测兜底本次不做**；③命名 `delete-notify-mq`；④**消息粒度改为按删除范围分流——删数据集传 `dataset_id`、删文件传 `original_file_id`**（比原「下发整批 file id」更优，从源头消除长列表撑爆消息体的问题，删数据集路径还可省去当前为收集 file id 的 `selectList` 查询）。据此引入 `delete_type`（dataset/file）判别字段；Python 按范围分流删除。brief §0/§1/§2/§3.1/§3.2/§3.5/§4/§5 全面重写：§4 移除「超大列表分批」风险、新增「Python 能否按 dataset_id 删」契约假设风险；§5 收敛为 2 个待确认（`delete_type` 显式 vs 推断、Python 按 dataset_id 删除的可行性需与 Python 侧确认）。待用户评审与冻结。
- 2026-05-30 **brief 冻结**：2 个待确认敲定——①**保留 `delete_type` 判别字段**（dataset/file），跨仓库契约显式标范围、不靠推断；②**数据集范围按 `dataset_id` 删除成立**：用户确认 Python 端尚未设计、将按本契约设计为支持按 `dataset_id` 批量删除，本 brief 契约即其设计输入（万一不支持再回退下发 file id 列表）。§3.5 假设/§4 风险相应降级为「移交 Python 设计的契约要求（低风险）」。决策全集固化，无遗留待确认项。进入 acceptance 阶段（acceptance-generator）。
- 2026-05-30 acceptance 首版（草稿）：基于冻结 brief 生成 9 个 Scenario（含 5 个 Scenario Outline），六大分类覆盖全部冻结决策。重点落为可断言 Then：①删文件→提交后投递 delete_type=file（含 original_file_id+dataset_id+user_id）；②删数据集→投递 delete_type=dataset（含 dataset_id+user_id、不含 original_file_id）；③删多文件数据集仍只一条 dataset 通知、消息体大小恒定（验证「不下发文件 id 列表」决策）；④消息形态不变量（扁平 JSON snake_case、点对点队列、恰含约定字段、无 trace/去重字段）；⑤事务回滚 verify(never)、删除未生效；⑥发送抛异常时删除仍成功返回、异常不外抛（兜底吞掉）；⑦发送前完整性校验缺字段拒发；⑧删除目标不存在/无权→404 且不投递；⑨删空数据集仍投递一条 dataset 通知。Then 只断言可观察契约行为，未落类名/判别列/selectList 等实现细节。待用户评审与冻结。
- 2026-05-30 **acceptance 冻结**：用户确认冻结（含「删空数据集仍投递一条 dataset 通知」的判断）。9 个 Scenario（含 5 Scenario Outline）与全部冻结决策对齐。进入 technical_design 阶段（technical-design）。
- 2026-05-30 technical_design 草稿：基于真实代码核实后定方案——①新增 `DocumentDeleteNotifyMQ`（implements AbstractMQ，`tolink.rag.document_delete`，QUEUE，扁平 JSON snake_case，`delete_type`+`dataset_id`+`user_id`+file 范围 `original_file_id`，`forDataset`/`forFile` 工厂 + `validate`，无参构造供拓扑扫描）；②`DocumentDeleteNotifier` 注入 `ObjectProvider<MQSend>`、改两语义方法 `notifyDatasetDeleted`/`notifyFileDeleted`、`send` try/catch 吞掉（null sender 告警吞掉，与 parse_task「抛出」刻意相反）；③`DatasetServiceImpl.delete` **移除仅为旧载荷的 selectList**（行 130-132）改调 dataset 范围通知，`DocumentFileServiceImpl.delete` 改调 file 范围通知；afterCommit 注册结构不改。核实既有测试影响：`DatasetServiceImplTest`（行 50/88-126）、`DocumentFileServiceImplTest`（行 57/98-126）的 `notifyAfterDelete` 断言改新方法名 + 移除 dataset 测试多余 `selectList` stub（严格桩）。新增 `DocumentDeleteNotifyMQTest`/`DocumentDeleteNotifierTest`，可选 `DeleteNotifyIntegrationTest`（端到端兜底）。fastjson 默认跳过 null 字段→dataset 不出现 original_file_id 键（单测 containsKey 守护）。9 Scenario 全映射。**识别发布前置风险**：Python 消费端未实现，QUEUE 无消费者会致 `document_delete` 队列积压——§12 列为待决策（建议加 producer 配置开关默认关 / 与 Python 上线协调 / 队列 TTL）。待 TD 审核 + 队列积压决策。
- 2026-05-30 发布前置决策（用户）：队列积压**靠上线协调**——代码先合、**不加配置开关**（producer 始终真实投递，保持最简），但 Java producer **不单独上生产**，待 Python 消费端就绪后两端一起发布（发布检查单/PR 显式标注此硬约束）。TD §11/§12/§14 同步收口。待用户审核放行进入实现（implementation-execution）。
- 2026-05-30 **TD 审核通过**：用户放行。消息模型 + notifier 两语义方法 + 两删除入口按范围分流 + 移除 dataset 多余 selectList + 失败吞掉 + 文档同步方案全部确认。进入实现阶段（implementation-execution），按 TD §13 顺序落地。
- 2026-05-30 **实现完成**：按 §13 落地——新增 `DocumentDeleteNotifyMQ`（QUEUE/snake_case/`delete_type` 分流/`validate`/工厂/无参构造）；`DocumentDeleteNotifier` 注入 `ObjectProvider<MQSend>`、两语义方法、失败吞掉不外抛；`DatasetServiceImpl` 移除多余 `selectList` 改调 `notifyDatasetDeleted`、`DocumentFileServiceImpl` 改调 `notifyFileDeleted`。新增 `DocumentDeleteNotifyMQTest`/`DocumentDeleteNotifierTest`，改 `DatasetServiceImplTest`（断言换 + 移除多余 stub 与孤儿 buildFile）/`DocumentFileServiceImplTest`（断言换 + 清 List import）。全量 `mvn test` BUILD SUCCESS（link-service 163、link-api 117）；doc-sync OK（17 files）、ai-links OK。同步 5 篇文档（mq_contracts/mq_module/integration/document_file_module/testing）。偏差 3 处见 implementation_report.md（未加可选整测、删孤儿 buildFile、dataset 测试加 times(1)/never file）。发布前置：Python 消费端未实现，producer 不单独上生产。进入质量审查（code-review-and-quality）。
- 2026-05-30 **质量审查 APPROVE**：6 维度（Correctness/Tests/Architecture/Security/Performance/Contracts）通过，0 Critical、0 Required。核实关键点：①afterCommit 发送失败 notifier 内吞掉不外抛、null sender 兜底、与 parse_task「失败外抛」差异有注释；②**启动安全**——核 `RabbitMQAutoConfiguration.rabbitMQDeclarables` 声明队列仅用 `getMQName/getMQType`、不调 `getMessage()`，故空 payload 实例化不触发 validate（无启动隐患）；③移除 `DatasetServiceImpl` 的 selectList 无其他消费者（`LambdaQueryWrapper`/`DocumentOriginalFile` 仍被别处用，import 不孤儿）、删除测试孤儿 buildFile/List import 干净；④契约字段/topic/snake_case 与文档一致、dataset 省略 original_file_id 由 containsKey 断言守护（测试通过即确认 fastjson 省 null 行为）；⑤Performance 净改善（少一次 selectList 查询）。2 个 Suggestion（非阻塞，记录）：①「发送失败删除仍成功」由组合覆盖（notifier 无条件吞掉单测 + service 调 notifier 单测），未加端到端整测（TD 已标可选，逻辑闭合）；②数据集删除按会话逐条删消息的 N+1 为既有、非本次引入。发布前置硬约束重申：Python 消费端就绪前 producer 不单独上生产。待 branch-pr-workflow。
- 2026-05-30 **提交 + PR**：从 dev（a432719）切 `feature/delete-notify-mq`，commit 0147f86（18 文件，+1137/-58），推送 origin，发起 PR #33 → dev（https://github.com/ql-link/LinkRag-Service/pull/33，base 干净=dev tip）。在 issue #29 评论说明 Java 半契约已定 + Python 第 2/3 部分待办 + 发布协调（https://github.com/ql-link/LinkRag-Service/issues/29#issuecomment-4583142523）。发布前置：Python 消费端未实现，producer 不单独上生产。
