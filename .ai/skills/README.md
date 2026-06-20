# Skills 注册表（toLink-Service）

本目录下每个子目录是一个 Agent skill（`<name>/SKILL.md`）。本文件是**索引 + 治理入口**，
帮助快速查找职责、理清触发边界、安排周期复审。

> 机器校验：`python3 scripts/check_skills.py`（pre-commit 已接入）。检查 frontmatter 完整性、
> 死引用、技术栈一致性（防 Python/RAG 模板腐化）、孤儿目录。新增/修改 skill 后请确保该检查为绿。
>
> 快速意图路由见 `.ai/prompts/project.md` 的「Skill 路由」表；本表是治理与复审全表。

## 管理约定

1. **单一职责 + 明确边界**：每个 skill 只解一类问题，`when_to_use` 必须写清「触发场景」与「转交规则」。
2. **Project-grounded**：示例、路径、命令一律用本仓库真实的（Maven 模块 `link-*/`、MyBatis-Plus、Kafka/RabbitMQ），不留 Python/RAG 残留。
3. **引用真实文件**：SKILL.md 里出现的 `link-*/ docs/ scripts/ .ai/ .claude/` 路径必须存在（省略包路径用 `.../`，占位用 `your_/xxx/<...>`）。
4. **增删有记录**：删除 skill 要在提交说明里写明，避免悬挂的「已删未提交」状态。
5. **本表与 `agents-tree-sync` 联动**：新增/删除/重命名 skill 时同步本表与 `AGENTS.md` 结构树。

## 按类别索引

### 需求 → 交付 流程链（按顺序）
| skill | 职责 | 边界 / 转交 |
| --- | --- | --- |
| `brief-generator` | 把新需求/想法收敛成开发者向 brief.md | 冻结后转 acceptance-generator |
| `acceptance-generator` | 基于冻结 brief 生成 Gherkin acceptance.feature | 需先有冻结 brief；要技术方案转 technical-design |
| `technical-design` | 基于 brief+acceptance 产出 technical_design.md | 上游缺失先回退对应 skill |
| `implementation-execution` | 方案确认后实现 Java 代码与测试，必要时产出 implementation_report.md | 无冻结 spec → 回 brief-generator；完成 → run-all-tests + code-review-and-quality |
| `run-all-tests` | 跑全量 Maven 测试并回报结论 | 收口前的测试关口 |
| `code-review-and-quality` | 提交/合并前质量门禁审查 | 过关后 → branch-pr-workflow |
| `branch-pr-workflow` | 从 dev 新建规范分支、提交并发起 PR | 链路终点；仅在测试 + 评审通过、收口时用 |

### 测试与质量
| skill | 职责 | 边界 / 转交 |
| --- | --- | --- |
| `auto-test` | 生成 JUnit/Mockito/MockMvc/SpringBootTest 测试，强调 Mock 隔离与边界覆盖 | 只写测试；红绿重构 → tdd |
| `tdd` | 红-绿-重构循环，先写测试 | 仅用户明确要 TDD 时 |
| `run-all-tests` | 跑全量 `mvn test` 并回报 | 见上 |
| `curl-api-test` | 对本地已启动服务构建并执行 curl 黑盒接口测试，覆盖边界，结果在对话返回 | 要 JUnit/MockMvc 单测转 auto-test；要验收契约转 acceptance-generator |
| `code-review-and-quality` | 五维质量门禁 | 安全专项可叠加内置 `/security-review` |
| `swagger-annotation` | 为 Controller / DTO 生成 SpringDoc 中文注解 | 仅注解，不改业务逻辑 |

### 数据库与数据模型
| skill | 职责 | 边界 / 转交 |
| --- | --- | --- |
| `mysql-ddl-conventions` | 建表/字段/索引规范（命名、类型、时间戳、引擎字符集、注释） | 改 Entity/Mapper 同步文档转 doc-maintenance-sync |

### 消息中间件
| skill | 职责 | 边界 / 转交 |
| --- | --- | --- |
| `mq-middleware` | MQ 中台收发、定义新消息类型、Kafka/RabbitMQ 多厂商适配 | 跨端 topic/字段一致性转 contract-guard |

### 契约 / 文档治理
| skill | 职责 | 边界 / 转交 |
| --- | --- | --- |
| `contract-guard` | 校验改动是否破坏 API/MySQL/Redis/MQ/OSS/错误码契约并给同步清单 | 泛化文档同步转 doc-maintenance-sync |
| `doc-maintenance-sync` | 代码/配置变更后同步 docs/AGENTS 等文档 | 项目结构树同步转 agents-tree-sync |
| `agents-tree-sync` | 同步 AGENTS.md 的「当前项目结构」树（非 docs 结构变更） | docs/ 结构变化不触发 |

### issue 协作
| skill | 职责 | 边界 / 转交 |
| --- | --- | --- |
| `cowork-issue-sync` | Linear + GitHub 双端建 issue 并双向回链（默认入口） | 完成实现发 PR 转 branch-pr-workflow |
| `issue-writer` | 仅 GitHub issue 的 fallback | 需双端同步转 cowork-issue-sync |

### 元能力（管理 skill 自身）
| skill | 职责 | 边界 / 转交 |
| --- | --- | --- |
| `skill-generator` | 从需求/流程创建全新 skill | 优化已有 skill 转 skill-optimizer |
| `skill-optimizer` | 优化/重写/评审已有 skill | 无已有输入的新建转 skill-generator |

### 内容产出
| skill | 职责 | 边界 / 转交 |
| --- | --- | --- |
| `blog-writer` | 基于真实需求/实现产出技术博客到 `.specs/blog/` | 仅指定时才参考历史博客 |

## 周期复审清单

每次大重构或里程碑后执行：

1. 跑 `python3 scripts/check_skills.py`，清掉所有 error。
2. 检查触发边界是否仍互斥（重叠的合并、过期的下线）。
3. 半年内从未触发的 skill 评估是否退役。
4. 同步更新本表与 `AGENTS.md` 结构树。
