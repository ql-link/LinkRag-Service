---
name: branch-pr-workflow
description: 实现完成后，从当前改动创建规范分支、提交并发起 PR。
when_to_use: "创建分支、提交、发 PR、把当前修改提 PR。"
---

# Branch PR Workflow

## 前提

- 不混入无关本地修改。
- 提交前运行测试和 doc-sync。
- PR base 默认 `dev`，除非用户明确指定。

## 分支命名

- `feature/<topic>`
- `fix/<topic>`
- `refactor/<topic>`
- `docs/<topic>`
- `chore/<topic>`

## PR 内容

包含 Summary、Changes、Tests、Risks。涉及 DB、MQ、Redis、OSS、内部接口时必须写运行时风险。
