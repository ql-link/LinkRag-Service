---
name: issue-writer
description: 发现 bug 或有新需求时，生成规范的 GitHub Issue 并通过 gh 创建。
when_to_use: "发现 bug、想提 issue、记录新需求、报告问题。触发示例：'提个 issue'、'把这个 bug 记一下'、'写个需求 issue'、'把这个问题提到 GitHub 上'"
---

# Issue Writer

## 定位

帮助用户快速生成规范的 GitHub Issue，支持两种类型：

- **Bug Report**：描述发现的问题、复现路径、预期与实际行为
- **Feature Request**：描述新需求的背景、目标和边界

## 工作流程

### 步骤 1：确定类型

从用户描述判断是 bug 还是新需求。不明确时追问，不要猜。

### 步骤 2：收集信息

**Bug Report 需要：**

- 问题描述（一句话）
- 复现路径（可以复现）或触发条件（偶现）
- 预期行为 vs 实际行为
- 影响范围：阻塞 / 功能异常 / 体验问题
- 相关代码位置（模块/接口/表名，若已知）

**Feature Request 需要：**

- 背景（为什么需要）
- 做什么（目标）
- 不做什么（边界）
- 优先级：高 / 中 / 低

信息不完整时从上下文补齐；关键项缺失（如 bug 无复现路径）则追问。

### 步骤 3：生成 Issue

**Bug Report 模板：**

```markdown
## 问题描述
<一句话>

## 复现路径
1. 
2. 
3. 

## 预期行为

## 实际行为

## 影响范围
阻塞 / 功能异常 / 体验问题

## 相关位置
<Controller / Service / 表名 / MQ Topic 等，若已知>
```

**Feature Request 模板：**

```markdown
## 背景
<为什么需要>

## 目标
<做什么>

## 不做什么

## 优先级
高 / 中 / 低
```

### 步骤 4：创建 Issue

```bash
gh issue create \
  --title "<标题>" \
  --body "$(cat <<'EOF'
<正文>
EOF
)" \
  --label "<bug 或 enhancement>"
```

若 `gh` 不可用，输出可直接复制的标题和正文。

## 约束

- 标题用中文，具体描述问题或目标，不写"修复 bug"、"新增功能"等泛泛措辞。
- Bug issue 不在正文里写修复方案（那是 brief/TD 的事）。
- Feature issue 不写实现细节，只写业务目标和边界。

## 衔接

- Bug 提完后，若需要立即着手修复，建议进入 `brief-generator` 分析影响范围。
- Feature Request 提完后，若要进入开发，建议进入 `brief-generator` 展开需求。
