# Issue Tracking Workflow

## 默认流程

当前项目默认通过 `cowork-issue-sync` 创建 issue，不再依赖 Linear team 到 GitHub 的自动同步。

目标是：

- 由 Agent 识别当前对话所属项目
- 自动映射到对应的 Linear project 与 GitHub 仓库
- 先创建 Linear issue，再创建 GitHub issue
- 在正文和 comment 中双向回链
- 把同一个负责人同步到两边

## 项目映射

| 项目 | Linear project | GitHub repo |
| --- | --- | --- |
| LinkRag | `LinkRag` | `ql-link/LinkRag` |
| LinkRag-Service | `LinkRag-Service` | `ql-link/LinkRag-Service` |
| LinkRag-Web | `LinkRag-Web` | `ql-link/LinkRag-Web` |

Linear team 固定为 `QIngluo`（team key `LINK`）。

## 何时使用哪个 skill

| 场景 | skill |
| --- | --- |
| 默认提 issue，需要 Linear 与 GitHub 双向同步 | `cowork-issue-sync` |
| 只需要 GitHub issue | `issue-writer` |
| issue 明确后进入需求分析 | `brief-generator` |
| 实现完成后提交与发 PR | `branch-pr-workflow` |

## 标准步骤

1. 识别项目归属：优先看当前仓库，再看对话中显式提到的项目名。
2. 判断 issue 类型：`Bug` / `Feature` / `Improvement`。
3. 生成标题与正文：从当前对话提炼背景、范围、边界和影响。
4. 确认负责人：从当前团队成员中选 1 人，并同步到两边。
5. 创建 Linear issue：先落 team 与 project，拿到 key 和 URL。
6. 创建 GitHub issue：正文直接带上 Linear 链接。
7. 补全回链：更新 Linear 正文中的 GitHub 链接，并在两边 comment 中互相回链。
8. 返回结果：给出两边链接、负责人、标签与部分失败说明。

## 标签规则

- 标签体系以 Linear 为主。
- 若 Linear 缺少类型标签，可先补建。
- GitHub 侧优先复用仓库已有语义标签；必要时允许补建。

## 失败处理

- Linear 创建失败：终止，不创建 GitHub issue。
- GitHub 创建失败：保留已建 Linear issue，并报告失败。
- comment 或正文回链失败：不回滚主 issue，但必须显式报告“回链不完整”。
- assignee 失败：不回滚 issue，但要在结果中说明哪一边分配失败。
