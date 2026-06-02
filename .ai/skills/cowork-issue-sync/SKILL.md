---
name: cowork-issue-sync
description: 当用户要提 issue 时使用；自动识别当前项目，生成结构化 issue 内容，先建 Linear 再建 GitHub，并在 Linear 中写入 GitHub issue 链接。
when_to_use: "提 issue、同步到 Linear、同步到 GitHub、用 Cowork 建需求、把当前需求记到 Linear 和 GitHub、不要再依赖 Linear 自动同步。"
---

# Cowork Issue Sync

## 1. 定位

这个 skill 用来替代 Linear team 级别的 GitHub 自动同步。

它负责：

- 判断当前对话属于哪个 LinkRag 项目
- 生成结构化 issue 标题与正文
- 在 Linear 与 GitHub 双端创建 issue
- 在 Linear 正文中写入 GitHub issue 链接
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

- `Bug`：异常、错误行为、回归、线上问题——走**修复 Bug 模板**
- `Feature`：新增能力、接口、流程、页面、协作能力——走**新增需求模板**
- `Improvement`：已有能力优化、流程改良、稳定性/效率/文档增强——走**新增需求模板**

若类型不明确，但从上下文可稳定判断，则直接判断；只有在 `Bug / Feature / Improvement` 语义冲突时才追问。

### 4.2 正文模板

以下模板用于 **Linear 和 GitHub 的正文主体**（内容相同）。Linear 正文额外追加 GitHub 链接由步骤 5 完成；GitHub 正文无需包含 Linear 链接。

---

**修复 Bug 模板**（类型 = `Bug`）

```markdown
## 背景

<受影响的功能/流程，以及发现问题的上下文>

## 问题描述

<一句话概括现象>

## 复现路径

1.
2.
3.

## 预期行为

## 实际行为

## 影响范围

<受影响的模块、接口、用户群体、频率等>

## 相关位置

<Controller / Service / 表名 / MQ Topic 等，若已知>

## 建议修复方向

<可选；若已有初步判断则填，不要编造>

## 验收要点

- [ ]
```

---

**新增需求模板**（类型 = `Feature` 或 `Improvement`）

```markdown
## 背景

<为什么需要，现状存在什么问题或机会，可附代码/日志/截图证据>

## 目标 / 本 issue 范围

- [ ]
- [ ]

## 不做什么

<明确边界，防止范围蔓延>

## 影响范围

<受影响的模块、接口、依赖方>

## 改进建议 / 实现思路

<可选；有明确方向时填写，不要过早下沉到代码细节>

## 风险 / 待确认

<已知不确定项，需要评审或拍板的决策点>

## 验收要点

- [ ]
```

---

生成正文时：

- 标题默认用中文，具体描述问题或目标，不写”修复 bug””新增功能”等空泛措辞
- 背景部分尽量引用已知代码位置、commit、或可观测证据，不凭空描述
- Bug 不提前写修复方案（放”建议修复方向”，且标注为可选）
- Feature / Improvement 不下沉到代码实现细节
- 验收要点必须可断言，写成可勾选的条目

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

在对应仓库创建 GitHub issue，正文使用步骤 2 生成的内容，不包含 Linear 链接。

GitHub issue 需同步：

- assignee：与 Linear 同一个人
- labels：按类型映射或补建
- title：与 Linear 保持同一语义，可允许轻微平台化措辞差异

记录返回值中的：

- issue number
- issue URL

### 步骤 5：在 Linear 正文补全 GitHub 链接

创建完成后，更新 Linear 正文，写入 GitHub issue URL。

GitHub issue 无需写入 Linear 链接，也无需额外 comment；Linear 也不发 comment。

### 步骤 6：报告结果

最终回复必须包含：

- 识别到的项目归属
- Linear issue key 与 URL
- GitHub issue 编号与 URL
- issue 类型
- assignee
- 使用或新建的 labels
- 是否存在部分失败（如 Linear 正文更新失败、assignee 失败）

## 7. 失败处理

- Linear 创建失败：终止流程，不创建 GitHub issue
- Linear 成功但 GitHub 创建失败：保留 Linear issue，并明确报告 GitHub 失败
- Linear 正文更新失败：不回滚主 issue，报告”主记录已创建，但 Linear 正文中的 GitHub 链接未写入”
- GitHub assignee 失败：不回滚 issue，报告分配失败并给出待人工处理项

## 8. 质量门禁

- 项目归属必须准确，不可把 issue 发到错误仓库
- Linear 正文必须包含 GitHub issue 链接；GitHub issue 无需包含 Linear 链接
- 标题不能空泛，正文不能缺失核心背景和边界
- 负责人必须在两边保持同一人
- 若有部分失败，必须显式报告，不能假装全成功

## 9. 与其他 skill 的衔接

- issue 明确后进入需求分析：转 `brief-generator`
- 完成实现后发 PR：转 `branch-pr-workflow`

---

## 10. 参考示例

以下为两种模板的填写示例，生成正文时以此为质量基准。

### 示例 A：修复 Bug

> **标题**：`POST /dataset/{id}/file` 上传文件后 OSS 路径写入空字符串

```markdown
## 背景

文件上传流程由 `DocumentFileService.uploadFile()` 处理，上传成功后将 OSS 路径写入
`knowledge_file.oss_path`。近期发现部分文件记录的 `oss_path` 为空字符串，导致后续
Python 侧解析任务找不到原文件。

## 问题描述

上传文件成功（HTTP 200）后，`knowledge_file.oss_path` 字段写入空字符串而非实际路径。

## 复现路径

1. 以合法用户调用 `POST /dataset/{id}/file`，上传任意 PDF 文件
2. 查询 `knowledge_file` 表对应记录
3. 观察 `oss_path` 字段值为 `""`

## 预期行为

`oss_path` 写入 OSS 返回的完整对象路径，如 `datasets/42/files/uuid.pdf`。

## 实际行为

`oss_path` 为空字符串 `""`；OSS 上文件实际已存在，仅路径未落库。

## 影响范围

- 受影响接口：`POST /dataset/{id}/file`
- 受影响记录：上传时间段内所有 `oss_path = ""` 的 `knowledge_file` 行
- 下游：Python 解析任务因路径为空无法拉取原文件，任务失败

## 相关位置

- `link-service/.../DocumentFileServiceImpl.uploadFile()`
- `link-mapper/.../KnowledgeFileMapper`
- `knowledge_file.oss_path` 字段

## 建议修复方向

初步怀疑 `OssClient.upload()` 返回值未正确赋给局部变量，导致写入了默认空值。
需核查 `uploadFile()` 中 OSS 返回路径的赋值链路。

## 验收要点

- [ ] 上传文件后 `knowledge_file.oss_path` 写入非空的完整 OSS 路径
- [ ] 存量 `oss_path = ""` 的记录有修复脚本或补偿方案说明
- [ ] 单测覆盖 `uploadFile()` 中 OSS 路径赋值逻辑
```

---

### 示例 B：新增需求（Improvement）

> **标题**：Java 端统一请求日志记录（接口入参 / 耗时 / 状态码）

```markdown
## 背景

`link-api` 目前无统一的请求/响应日志中间件。各接口日志依赖 Service 方法内散点打印，
排查问题时需逐层翻代码。`grep` 关键词后往往跨 3–4 个类才能还原一次请求的完整路径。

现有日志示例（不统一）：
- `UserController`：无入参日志
- `ConfigController`：仅在异常分支打印
- `DatasetController`：部分方法有，部分没有

## 目标 / 本 issue 范围

- [ ] 在 `link-api` 新增 `RequestLoggingFilter`（`OncePerRequestFilter` 实现）
- [ ] 统一记录：请求路径、HTTP 方法、入参（敏感字段脱敏）、响应状态码、耗时（ms）
- [ ] 敏感字段（`password`、`token`、`apiKey`）脱敏后输出，不写原值

## 不做什么

- 不引入分布式链路追踪（TraceId 透传可作独立 issue）
- 不集成 ELK 等外部日志系统
- 不对文件上传接口记录 request body（防止日志过大）

## 影响范围

- `link-api` 模块，新增一个 Filter，不改动 Controller 业务逻辑
- 日志量会有所增加，需评估是否影响磁盘/输出性能

## 改进建议 / 实现思路

继承 `OncePerRequestFilter`，在 `doFilterInternal` 中用 `ContentCachingRequestWrapper`
包装请求，拦截 body；响应完成后统一打印。脱敏可用简单正则替换。

## 风险 / 待确认

- `ContentCachingRequestWrapper` 会在内存中缓存 body，大文件上传接口需排除在外
- 日志级别建议 `DEBUG`，生产环境默认关闭，待与运维确认日志策略

## 验收要点

- [ ] 调用 `POST /user/login`，日志中可见路径、耗时、状态码，`password` 字段脱敏
- [ ] 调用不存在路径，日志中可见 404 及耗时
- [ ] 文件上传接口日志中不出现 body 内容
- [ ] 单测验证脱敏逻辑
```
