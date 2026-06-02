---
name: cowork-issue-sync
description: 当用户要提 issue，且需要同时在 Linear 与 GitHub 建立双向关联时使用；自动识别当前项目，生成结构化 issue 内容，先建 Linear 再建 GitHub，并在正文与 comment 中互相回链。
when_to_use: "提 issue、同步到 Linear、同步到 GitHub、用 Cowork 建需求、把当前需求记到 Linear 和 GitHub、不要再依赖 Linear 自动同步。若用户明确只想建 GitHub issue，或 Linear 不可用且用户接受降级，则转 issue-writer。"
---

# Cowork Issue Sync

## 1. 定位

这个 skill 用来替代 Linear team 级别的 GitHub 自动同步。

它负责：

- 判断当前对话属于哪个 LinkRag 项目
- 生成结构化 issue 标题与正文
- 在 Linear 与 GitHub 双端创建 issue
- 给两边写入正文回链和 comment 回链
- 把同一个负责人同步到 Linear 与 GitHub
- 按 issue 类型补齐或复用标签

它不负责：

- 创建分支、提交、发 PR（转 `branch-pr-workflow`）
- 跟踪 issue 后续状态流转
- 做重复 issue 合并或自动关闭

## 2. 触发边界

### 2.1 适合使用

- 用户说“提个 issue”，且项目协作基于 Linear
- 用户要求“同步到 Linear 和 GitHub”
- 用户明确说“不要依赖 Linear 自动同步”
- 当前需求需要形成跨平台跟踪链接

### 2.2 不适合使用

- 用户明确只要建 GitHub issue，不需要 Linear
- 用户只是在讨论需求，还没准备登记 issue
- 用户要发 PR、建分支、推送代码

## 3. 前置上下文

### 3.1 Linear team 与项目

- Team 固定使用 `QIngluo`
- Team key 固定为 `LINK`

项目映射采用直接映射：

| 对话/仓库归属 | Linear project | GitHub repo |
| --- | --- | --- |
| Python 端 / `LinkRag` | `LinkRag` | `ql-link/LinkRag` |
| Java 端 / `LinkRag-Service` | `LinkRag-Service` | `ql-link/LinkRag-Service` |
| 前端 / `LinkRag-Web` | `LinkRag-Web` | `ql-link/LinkRag-Web` |

### 3.2 可选负责人

当前支持的 4 位成员：

- `bianyuning`
- `zhan82789`
- `yyifan355`
- `jixu0090`

若用户未指定负责人，且上下文无法可靠推断，则必须追问一次，不自动猜。

## 4. issue 类型与模板

### 4.1 类型判定

- `Bug`：异常、错误行为、回归、线上问题、复现路径
- `Feature`：新增能力、接口、流程、页面、协作能力
- `Improvement`：已有能力优化、流程改良、稳定性/效率/文档增强

若类型不明确，但从上下文可稳定判断，则直接判断；只有在 `Bug / Feature / Improvement` 语义冲突时才追问。

### 4.2 正文模板

**Bug**

```markdown
## 问题描述

## 复现路径 / 触发条件
1.
2.
3.

## 预期行为

## 实际行为

## 影响范围

## 相关位置

## Linear / GitHub 同步
- Linear: <创建后回填>
- GitHub: <创建后回填>
```

**Feature**

```markdown
## 背景

## 目标

## 不做什么

## 业务价值

## 影响范围

## Linear / GitHub 同步
- Linear: <创建后回填>
- GitHub: <创建后回填>
```

**Improvement**

```markdown
## 现状问题

## 改进目标

## 约束

## 不做什么

## 预期收益

## Linear / GitHub 同步
- Linear: <创建后回填>
- GitHub: <创建后回填>
```

生成正文时：

- 标题默认用中文，具体描述问题或目标
- 不写空泛标题，如“修复 bug”“新增功能”
- Bug 不提前写修复方案
- Feature / Improvement 不下沉到实现细节

## 5. 标签规则

Linear 作为标签体系的 canonical 来源：

- `Bug`
- `Feature`
- `Improvement`

执行规则：

1. 先检查 Linear 是否存在目标标签。
2. 若 Linear 缺失目标标签，可先补建再创建 issue。
3. GitHub 侧优先复用语义最接近的现有标签：
   - `Bug` -> 优先 `bug`
   - `Feature` -> 优先 `Feature`，否则 `enhancement`
   - `Improvement` -> 优先 `Improvement`
4. 若 GitHub 仓库也缺失必要标签，可补建对应标签，再创建 issue。

不要因为标签缺失直接放弃整个 issue 创建流程；应优先补齐或降级映射。

## 6. 工作步骤

### 步骤 1：识别项目归属

按以下优先级判断：

1. 当前工作区仓库 remote / repo 名称
2. 用户明确提到的项目名（Python 端 / Java 端 / 前端）
3. 若仍不明确，追问一次

禁止在项目不明确时盲猜 project 或 repo。

### 步骤 2：整理 issue 类型、正文与负责人

- 判断 issue 类型：`Bug` / `Feature` / `Improvement`
- 从当前对话中提取背景、目标、边界、影响范围、相关位置
- 生成中文标题和结构化正文
- 确认负责人；未指定且无法推断时追问一次

### 步骤 3：创建 Linear issue

必须先创建 Linear issue，写入：

- team：`QIngluo`
- project：按映射表
- assignee：用户选定负责人
- labels：按类型补齐
- title / description：使用生成后的内容

记录返回值中的：

- issue key（如 `LINK-35`）
- issue id
- issue URL

### 步骤 4：创建 GitHub issue

在对应仓库创建 GitHub issue，创建时正文必须直接包含 Linear issue 链接。

GitHub issue 需同步：

- assignee：与 Linear 同一个人
- labels：按类型映射或补建
- title：与 Linear 保持同一语义，可允许轻微平台化措辞差异

记录返回值中的：

- issue number
- issue URL

### 步骤 5：补全双向回链

创建完成后，必须补齐两边的正文与 comment：

- 更新 Linear 正文，写入 GitHub issue URL
- 在 Linear 下发一条 comment，说明 GitHub issue 已创建并附链接
- 在 GitHub 下发一条 comment，说明 Linear issue 已创建并附链接

GitHub 正文因为创建时已带 Linear 链接，通常无需二次更新；除非创建命令阶段未写入成功。

### 步骤 6：报告结果

最终回复必须包含：

- 识别到的项目归属
- Linear issue key 与 URL
- GitHub issue 编号与 URL
- issue 类型
- assignee
- 使用或新建的 labels
- 是否存在部分失败（如正文更新成功/失败、comment 成功/失败、assignee 成功/失败）

## 7. 失败处理

- Linear 创建失败：终止流程，不创建 GitHub issue
- Linear 成功但 GitHub 创建失败：保留 Linear issue，并明确报告 GitHub 失败
- comment 或正文更新失败：不回滚主 issue，报告“主记录已创建，但回链不完整”
- GitHub assignee 失败：不回滚 issue，报告分配失败并给出待人工处理项

## 8. 质量门禁

- 项目归属必须准确，不可把 issue 发到错误仓库
- 两边都要有对方链接，且正文与 comment 至少各有一处
- 标题不能空泛，正文不能缺失核心背景和边界
- 负责人必须在两边保持同一人
- 若有部分失败，必须显式报告，不能假装全成功

## 9. 与其他 skill 的衔接

- 只建 GitHub issue：转 `issue-writer`
- issue 明确后进入需求分析：转 `brief-generator`
- 完成实现后发 PR：转 `branch-pr-workflow`
