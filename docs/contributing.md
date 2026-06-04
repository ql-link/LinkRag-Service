# Contributing — toLink-Service

贡献者入口。本文件是**总纲 + 路由**：流程总览见此，完整约定在 [CLAUDE.md](../CLAUDE.md) 与对应 skill。

## 开发流程：Spec-as-Test

新需求按 `brief.md → acceptance.feature → technical_design.md → Code + Tests` 推进，每阶段有门禁（前一个冻结才进下一个）。

- 流程与阶段门禁详见 [CLAUDE.md「二、Spec-as-Test」](../CLAUDE.md)
- 产物放 `.specs/<需求名>/`（本地工作产物，不进 repo，合并后清理）
- skill：`brief-generator` → `acceptance-generator` → `technical-design` → `implementation-execution`

## 分支与提交

- 分支前缀：`feature/` `fix/` `refactor/` `docs/` `chore/` + 英文 kebab-case
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
