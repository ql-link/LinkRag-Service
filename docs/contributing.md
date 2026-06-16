# Contributing — toLink-Service

贡献者入口。本文件是**总纲 + 路由**：流程总览见此，完整约定在 [CLAUDE.md](../CLAUDE.md) 与对应 skill。

## 开发流程：Spec-as-Test

新需求按 `brief.md → acceptance.feature → technical_design.md → Code + Tests` 推进，每阶段有门禁（前一个冻结才进下一个）。

- 流程与阶段门禁详见 [CLAUDE.md「二、Spec-as-Test」](../CLAUDE.md)
- 产物放 `.specs/<需求名>/`（本地工作产物，不进 repo，合并后清理）
- skill：`brief-generator` → `acceptance-generator` → `technical-design` → `implementation-execution`

## 分支与发布

- `dev` 是日常集成分支；`master` 是稳定发布分支。本仓库默认分支为 `master`，文档和 CI 不使用 `main`。
- 日常开发分支使用 `feature/`、`refactor/`、`chore/`、`fix/`、`docs/` + 英文 kebab-case，PR 默认合入 `dev`。
- `master` 不接受日常 `feature/`、`refactor/`、`chore/` 直接合入。
- 每周发布从 `dev` 拉出 `release/<version>` 发布准备分支，通过 release PR 合入 `master`。
- `dev` / `release/<version>` 到 `master` 的发布合并必须使用普通 merge commit，禁止 squash merge。
- release PR 描述必须列出包含的业务 PR、数据库/配置/契约变更、测试结果和风险。
- release PR 合入 `master` 后，在 `master` 的发布 merge commit 上打版本 tag。
- `hotfix/<topic>` 必须从 `master` 拉出，修复后 PR 合入 `master`；发布后必须 merge 或 cherry-pick 回 `dev`，避免修复只存在于发布线。
- 提交信息用 Conventional Commits（`feat` / `fix` / `refactor` / `docs` / `chore`）
- 发 PR 用 `branch-pr-workflow` skill（含分支命名约定与 PR 描述模板）

## 测试

- 全量 `mvn test`；单模块 `mvn -pl <module> test`
- 规范见 [internals/testing.md](internals/testing.md)；写/补测试用 `auto-test`，跑全量用 `run-all-tests`

## 文档同步（机器强制）

高风险改动必须同步对应文档，否则 pre-commit 阻断：

- API / DTO 变更 → `docs/api/api_contracts.md`
- Entity / 数据库脚本 → `docs/api/mysql_schema.md`
- MQ 消息 / 消费者 → `docs/api/mq_contracts.md`、`docs/internals/mq_module.md`
- 规则在 `.claude/doc-sync-rules.yaml`，由 pre-commit 三道门禁强制

提交前自检：

```bash
python3 scripts/check_ai_links.py
python3 scripts/check_docs_sync.py --working
python3 scripts/check_skills.py
```

## Skill 与项目结构

- 改 / 增 skill 后跑 `check_skills.py`；skill 清单与治理见 [.ai/skills/README.md](../.ai/skills/README.md)
- 非 docs 目录结构变动（新增模块、迁脚本等）同步 `AGENTS.md` 的结构树（`agents-tree-sync` skill）
