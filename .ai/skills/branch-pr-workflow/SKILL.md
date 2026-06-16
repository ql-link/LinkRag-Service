---
name: branch-pr-workflow
description: 实现完成后，从当前改动创建规范分支、提交并发起 PR。
when_to_use: "创建分支、提交、发 PR、把当前修改提 PR。"
---

# Branch PR Workflow

## 前提检查

执行前必须确认：

1. 运行 `git branch --show-current` 检查当前分支。
2. 若当前分支不是预期的基础分支（通常为 `dev`），停止并告知用户，不自动切换分支。
3. 运行 `git status --short` 确认工作区状态，识别无关改动。

## 分支命名

| 前缀 | 适用场景 |
| --- | --- |
| `feature/` | 新增业务能力、接口、流程 |
| `fix/` | Bug 修复 |
| `refactor/` | 重构、结构调整，无新增业务能力 |
| `docs/` | 纯文档修改 |
| `chore/` | 构建、配置、脚本调整 |

主题使用英文 kebab-case：`feature/recall-gateway`、`fix/mq-duplicate-consume`。
避免泛泛名称，如 `feature/update`、`fix/bug`。

## 分支模型

- `dev` 是日常集成分支；`master` 是稳定发布分支。
- 日常 `feature/`、`refactor/`、`chore/`、`fix/`、`docs/` 分支默认从 `dev` 拉出并 PR 回 `dev`。
- `master` 不接受日常 `feature/`、`refactor/`、`chore/` 直接合入。
- 每周发布从 `dev` 拉出 `release/<version>`，通过 release PR 合入 `master`。
- `dev` / `release/<version>` 到 `master` 的发布合并必须使用普通 merge commit，禁止 squash merge。
- release PR 描述必须列出包含的业务 PR、数据库/配置/契约变更、测试结果和风险。
- release PR 合入 `master` 后，在 `master` 的发布 merge commit 上打版本 tag。
- `hotfix/<topic>` 从 `master` 拉出，PR 合入 `master` 后必须 merge 或 cherry-pick 回 `dev`。

## 工作流程

### 步骤 1：理解改动范围

```bash
git diff --stat
git status --short --branch
```

识别哪些改动属于本次需求，哪些是无关修改。若有无关改动，告知用户，只暂存相关文件。

### 步骤 2：运行测试和文档校验

```bash
mvn -pl <module> test          # 或 mvn test（全量）
python3 scripts/check_docs_sync.py --working
```

若测试或校验失败，停止并报告，不继续提交。

### 步骤 3：创建分支

```bash
git switch -c <branch-name>
```

当前未提交改动会随工作区留在新分支上。

### 步骤 4：提交

只暂存本次相关文件，不用 `git add -A`。

提交信息使用约定式提交（Conventional Commits）：

```text
feat(模块): 简短描述（不超过 70 字符）

- 改动点 1
- 改动点 2
```

前缀与分支前缀对应：`feat` / `fix` / `refactor` / `docs` / `chore`。

### 步骤 5：更新 feature_info.md

提 PR 前将 `.specs/<需求名>/feature_info.md` 状态更新为 `PR 待合并`，并将本次提交纳入暂存，一并提交或单独提交均可。这样 feature 状态变更才能随分支推上去。

### 步骤 6：推送并创建 PR

```bash
git push -u origin <branch-name>
```

PR base 默认 `dev`。仅 release PR 使用 `master` 作为 base；hotfix PR 先合入 `master`，发布后必须回合 `dev`。

**关联 Issue**：检查 `.specs/<需求名>/feature_info.md` 和当前对话上下文中是否有 GitHub issue 号。有则在 PR 正文开头加 `Closes #<issue号>`，没有则跳过，不追问用户。

优先用 `gh pr create`：

```bash
gh pr create --title "..." --body "$(cat <<'EOF'
Closes #<issue号>

## Summary
...
EOF
)"
```

### 步骤 7：在 Issue 下回复解决思路

PR 创建成功后，若有关联 issue，在该 issue 下发一条评论，说明解决思路：

```bash
gh issue comment <issue号> --body "$(cat <<'EOF'
已在 PR #<PR号> 中实现。

**解决思路：**
<2-3 句话说明核心方案，例如：在哪里改了什么、用了什么机制解决问题>
EOF
)"
```

评论只写思路，不贴代码，不重复 PR 描述的全部内容。

## PR 内容

必须包含：

```markdown
Closes #<issue号>      ← 若有关联 issue；无则省略

## Summary
- 解决了什么问题
- 核心实现方式

## Changes
- 主要代码改动
- 配置、文档、测试改动

## Tests
- 实际运行的测试命令
- 测试结果

## Risks
- 兼容性、数据、MQ、OSS、回滚风险
- 若无明显风险写 No known high-risk items
```

涉及 DB、MQ、Redis、OSS 或 Java/Python 跨端协作时，必须在 Risks 中说明运行时前提和影响。

## 最终回复

必须包含：

- 创建的分支名
- 提交哈希和提交信息
- PR URL；若无法创建，给出可手动使用的标题和描述
- 已运行的测试命令和结果
- 是否有未纳入本次提交的本地改动
