---
name: branch-pr-workflow
description: 实现完成后，从当前改动创建规范分支、提交并发起 PR。
when_to_use: "创建分支、提交、发 PR、把当前修改提 PR。"
---

# Branch PR Workflow

## 前提检查

执行前必须确认：

1. 运行 `git branch --show-current` 检查当前分支。
2. 新任务必须以最新 `origin/master` 为基线；若当前分支的基线不是 `master`，停止并告知用户，不自动改写已发布分支历史。只有用户明确要求把现有分支迁移到新流程时，才可在确认 `dev` 与 `master` 差异后执行，并使用 `--force-with-lease` 安全更新远程分支。
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

- `master` 是稳定发布分支和所有新分支的唯一基线；`dev` 只用于开发环境集成、构建和验收。
- 所有 `feature/`、`refactor/`、`chore/`、`fix/`、`docs/`、`hotfix/` 分支都从最新 `origin/master` 拉出。
- 同一候选分支先 PR 合入 `dev`；开发环境构建和真实验收通过后，保持 HEAD SHA 不变，再 PR 合入 `master` 发布。
- 禁止将 `dev` 整体合入 `master`，也不再以 `release/<version>` 承载日常发布，避免带入其他尚未批准的集成改动。
- 候选分支合入 `dev` 后必须保留远程分支。开发环境验收后若又产生新提交，必须重新合入 `dev` 并重跑构建与验收。
- 候选分支到 `master` 的发布 PR 必须使用普通 merge commit，禁止 squash merge，并记录开发环境构建、验收证据、契约变更和风险。
- 发布 PR 合入 `master` 后，在 `master` 的发布 merge commit 上打版本 tag。

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

### 步骤 3：从 master 创建分支

```bash
git fetch origin master --prune
git switch master
git pull --ff-only origin master
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

### 步骤 6：推送并创建 dev 集成 PR

```bash
git push -u origin <branch-name>
```

PR base 显式指定为 `dev`。该 PR 只用于触发开发环境集成和验收；合入后不得删除当前远程分支。

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

### 步骤 7：开发环境构建与验收

1. 等待 `dev` 构建完成，记录构建编号、结果和部署镜像/提交。
2. 在开发环境运行与改动风险相匹配的真实验收，不得只以本地单测代替。
3. 记录候选分支 `git rev-parse HEAD` 的完整 SHA。
4. 若构建或验收失败，在同一分支修复，重新 PR 合入 `dev`，然后重跑本步骤。

### 步骤 8：用同一分支创建 master 发布 PR

只有开发环境构建和验收通过后才可执行。创建前必须确认本地、远程分支与验收记录中的 HEAD SHA 完全一致：

```bash
git fetch origin master --prune
git rev-parse HEAD
git rev-parse origin/<branch-name>
gh pr create --base master --head <branch-name> --title "..." --body-file <release-pr-body.md>
```

发布 PR 必须保持 Draft，直到发布检查完成；合并时使用普通 merge commit，禁止 squash merge。

### 步骤 9：在 Issue 下回复解决思路

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

`master` 发布 PR 还必须增加：

```markdown
## Dev Validation
- Candidate SHA: <已验收的完整 SHA>
- Build: <开发环境构建链接/编号与结果>
- Environment checks: <真实环境验收命令与结果>
```

## 最终回复

必须包含：

- 创建的分支名
- 提交哈希和提交信息
- `dev` 集成 PR 与 `master` 发布 PR URL；未达到开发环境门禁时明确说明发布 PR 尚未创建
- 已运行的测试命令和结果
- 是否有未纳入本次提交的本地改动
