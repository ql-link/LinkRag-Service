# Documentation Sync

本项目通过 `.claude/doc-sync-rules.yaml` 约束代码和文档同步。

## 组成

```text
.claude/doc-sync-rules.yaml
scripts/check_docs_sync.py
scripts/check_ai_links.py        # .ai 资产链接完整性
scripts/check_skills.py          # .ai/skills/*/SKILL.md 校验
.pre-commit-config.yaml          # pre-commit 三道门禁：check-docs-sync / check-ai-links / check-skills
.github/workflows/docs-sync.yml
```

## 常用命令

```bash
python3 scripts/check_docs_sync.py --self-check
python3 scripts/check_docs_sync.py --working
python3 scripts/check_docs_sync.py --staged
python3 scripts/check_docs_sync.py --base origin/dev
```

## 规则原则

- API、数据库、MQ 这类对接契约（落在 `docs/api/`）使用 `error`，漏同步会阻止提交或合并。
- 内部架构说明（`docs/internals/`）使用 `warning`，提醒同步但不默认阻塞。
- `.ai`、入口文档、同步脚本变更必须同步开发流程文档。
- skill 变更由 `check_skills.py`（pre-commit `check-skills` 门禁）校验 frontmatter、死引用、技术栈一致性与孤儿目录。

新增规则后必须运行：

```bash
python3 scripts/check_docs_sync.py --self-check
```
