---
name: branch-history-reconciliation
description: 当两个分支都基于同一祖先做了高度重叠的重构，主分支已吸收功能语义但因为提交历史不同而出现大量冲突时使用。用于判断是直接标记历史已吸收、只迁移独有增量，还是需要重建整理分支。
when_to_use: "当用户提到 refactor 分支、主分支已部分合入、重复重构、Git 冲突很多、树内容可能一致、需要快速决定 merge/cherry-pick/ours merge 策略时激活"
---

# 分支历史收敛工作流

## 目标

处理这类场景：

- `dev` 和某个重构分支都从同一祖先分出
- 两边做了高度重叠的目录重构、包迁移、接口改签名
- 主分支已经吸收了大部分语义，但没有保留祖先关系
- 直接 merge 会产生大量 rename/modify 冲突

这个 skill 的目标不是“把冲突一个个解掉”，而是先判断：

1. 两个分支的最终树是否已经一致
2. 是否只剩历史未收敛的问题
3. 是否还有主分支没有的真实增量

## 核心原则

1. 先判断“代码树是否一致”，再决定 merge 策略
2. 不要在脏工作区直接处理历史收敛问题
3. 不要默认对重构分支做普通 merge
4. 不要把“同语义双重重构”当作普通功能分支回合并
5. 优先把老分支降级为“补丁来源”而不是“必须完整合并的主分支”

## 快速判断流程

### Step 1: 找共同祖先

```bash
git merge-base <target-branch> <source-branch>
```

如果共同祖先很早，而两边都各自演化了重构提交，说明后续冲突大概率是历史问题，不是业务问题。

### Step 2: 看独有提交

```bash
git log --left-right --cherry-pick --oneline <target-branch>...<source-branch>
git range-diff <merge-base>..<target-branch> <merge-base>..<source-branch>
```

重点看：

- 是否两边顶部提交语义接近，但 hash 不同
- 是否只是作者、提交信息、路径调整不同
- 是否真的存在主分支没有的业务提交

### Step 3: 看最终树是否一致

先看整体差异：

```bash
git diff --stat <target-branch>..<source-branch>
git diff --name-status <target-branch>..<source-branch>
```

再直接比 tree：

```bash
git rev-parse <target-branch>^{tree} <source-branch>^{tree}
```

如果两个 tree hash 完全一致，说明：

- 两边当前代码内容相同
- 冲突只来自历史没收敛
- 不应该再去逐文件解冲突

## 决策树

### 情况 A：两个分支 tree 完全一致

这是最理想场景。

处理方式：

1. 基于目标分支新开整理分支
2. 用 `ours` 策略合并源分支
3. 提一个“历史收敛 PR”

命令：

```bash
git worktree add /tmp/reconcile-branch <target-branch>
cd /tmp/reconcile-branch
git checkout -b codex/reconcile-<short-name>
git merge -s ours --no-ff <source-branch> -m "merge: mark <source-branch> as already absorbed by <target-branch>"
```

适用原因：

- 保留目标分支当前代码不变
- 把源分支历史正式标记为“已吸收”
- 后续 PR 不会再重复炸出同样的大冲突

### 情况 B：tree 不一致，但差异很小

处理方式：

1. 基于目标分支新开整理分支
2. 不直接 merge 老分支
3. 只迁移主分支没有的真实增量

优先用：

```bash
git cherry-pick <commit>
```

或手工拣入少量文件修改。

判断标准：

- 只有少量配置、测试、注释、局部逻辑不同
- 差异集中在少量文件
- 主分支已经具备主体结构和大部分语义

### 情况 C：tree 不一致，而且真实增量较多

处理方式：

1. 仍然基于目标分支新开整理分支
2. 把源分支当作“补丁来源”
3. 按模块迁移真正独有功能
4. 迁移完成后再提 PR

此时不要直接：

- `git merge <source-branch>`
- `git rebase <target-branch>`

因为大规模重构叠加 rename/modify 冲突时，这两种方式成本最高，且很容易留下重复实现。

## 推荐操作顺序

### 1. 保护当前工作区

如果当前目录是脏的，不在原地操作。

优先使用：

```bash
git worktree add /tmp/reconcile-branch <target-branch>
```

### 2. 用整理分支承接历史收敛

统一在新分支做，不污染原来的重构分支。

命名建议：

- `codex/reconcile-<source-branch-short-name>`
- `codex/merge-history-<module>`

### 3. 验证是否真有代码差异

如果 `git diff <target>..<source>` 是空的，直接走 `ours merge`。

### 4. 只在必要时迁移独有增量

独有增量才值得 `cherry-pick` 或手工迁移。

## 禁止事项

以下做法在这类场景里通常是错误的：

1. 在脏工作区直接 merge 重构分支
2. 对已经被主分支部分吸收的老重构分支做完整 rebase
3. 逐文件硬解所有 rename/modify 冲突
4. 不验证 tree 是否一致就默认代码不同
5. 把“历史问题”误判成“业务冲突”

## 输出结论模板

处理完后，结论应明确落到这 3 类之一：

### 模板 1：历史已吸收

```text
<target-branch> 和 <source-branch> 的 tree 完全一致，代码内容相同。
不需要迁移代码，只需要用 ours merge 收敛历史。
```

### 模板 2：仅迁移独有增量

```text
主分支已吸收主体重构，源分支只剩少量独有增量。
不建议直接 merge，建议基于主分支新开整理分支，逐个 cherry-pick 独有提交。
```

### 模板 3：需要重建整理分支

```text
两个分支共享祖先但后续独立重构过深，真实差异仍然较大。
不建议直接 merge 或 rebase，建议基于主分支新开整理分支，按模块迁移独有改动。
```

## 适用信号

遇到以下信号时，优先启用本 skill：

- “这个分支其实 dev 已经合了一部分”
- “提 PR 时冲突特别多”
- “看起来改的是同一批重构”
- “路径改了、包也改了、接口也动了”
- “代码最后效果几乎一样，但 Git 还是显示很多差异”

