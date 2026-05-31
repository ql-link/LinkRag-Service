# Documentation Sync

本项目通过 `.claude/doc-sync-rules.yaml` 约束代码和文档同步。

## 组成

```text
.claude/doc-sync-rules.yaml
scripts/check_docs_sync.py
.pre-commit-config.yaml
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

- API、数据库、MQ 这类对接契约使用 `error`，漏同步会阻止提交或合并。
- 内部架构说明使用 `warning`，提醒同步但不默认阻塞。
- `.ai`、入口文档、同步脚本变更必须同步开发流程文档。

新增规则后必须运行：

```bash
python3 scripts/check_docs_sync.py --self-check
```
