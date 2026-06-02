# feature_info

| 项 | 值 |
| :--- | :--- |
| 需求名 | cowork-linear-github-issue-sync |
| 中文名 | Cowork Skill：Linear 与 GitHub issue 手动双向同步 |
| 来源 | 当前对话需求：取消 Linear team 对 GitHub 的自动关联，改为通过 Cowork Skill 手动完成 Linear → GitHub issue 同步 |
| 分支 | feature/ai-workflow-skills（现有分支继续演进） |
| 当前阶段 | brief 草稿待确认（2026-06-02） |
| brief.md | 已生成（2026-06-02，待冻结） |
| acceptance.feature | 未开始 |
| technical_design.md | 未开始 |
| implementation_report.md | 未开始 |

## 已确认业务约束

| 约束项 | 当前决策 |
| :--- | :--- |
| Linear team | 使用 `QIngluo`（team key `LINK`） |
| 项目映射 | 直接映射：`LinkRag` / `LinkRag-Service` / `LinkRag-Web` |
| GitHub 仓库映射 | 直接映射：`ql-link/LinkRag` / `ql-link/LinkRag-Service` / `ql-link/LinkRag-Web` |
| 创建顺序 | 先创建 Linear issue，再创建 GitHub issue |
| 负责人 | Linear 与 GitHub 双写，同一个人 |
| 回链方式 | 正文和 comment 都放对方链接 |
| 标签体系 | 以 Linear 为主；Linear 缺失时允许补充 |

## 阶段记录

- 2026-06-02 现状核实：当前分支的 `issue-writer` 仍是 GitHub-only，使用 `gh issue create` 创建 issue，不覆盖 Linear；`branch-pr-workflow` 负责 PR 流程，与本需求不直接重叠。
- 2026-06-02 外部系统核实：
  - Linear 当前只有 1 个相关 team：`QIngluo`（key `LINK`）。
  - Linear 当前存在 3 个目标 project：`LinkRag`、`LinkRag-Service`、`LinkRag-Web`。
  - Linear 当前可选 4 个真人成员：`bianyuning`、`zhan82789`、`yyifan355`、`jixu0090`。
  - GitHub 目标仓库存在且名称与项目直接对应：`ql-link/LinkRag`、`ql-link/LinkRag-Service`、`ql-link/LinkRag-Web`。
- 2026-06-02 用户确认四个关键决策：
  - 项目采用直接映射，不引入额外命名转换。
  - 负责人在 Linear 与 GitHub 双写，保持为同一个人。
  - 双向链接既写入正文，也写入 comment。
  - 标签体系以 Linear 为准，Linear 不全时允许补充标签。
- 2026-06-02 保守实现决策：
  - 新增独立 skill `cowork-issue-sync` 作为默认入口，不直接覆盖旧 `issue-writer`。
  - 旧 `issue-writer` 保留为 GitHub-only fallback，适用于用户明确只要 GitHub issue 或 Linear 不可用的场景。
  - 同步补充 `docs/development/issue_tracking_workflow.md`，并更新项目入口与开发文档说明。
