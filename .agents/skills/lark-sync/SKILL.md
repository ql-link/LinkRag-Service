---
name: lark-sync
description: 当用户明确要求同步飞书、归档本地开发文档、填写 toLink 开发进度表或检查 Lark 共享文件夹时使用。轻量需求默认不触发；本 skill 负责规划并直接执行本地文档导入飞书共享文件夹、以及将文档链接写回 Lark 多维表格；仅在冲突、覆盖、新建记录或权限缺失等红旗场景暂停确认。
when_to_use: 用户说“同步到飞书”“归档文档”“填进度表”“更新 Lark 表格”“放到共享文件夹”，或需要检查/维护 toLink Lark 固定资源入口时使用。
---

# Lark 轻量同步

## 定位

本 skill 只处理 ToLink 项目的 Lark / 飞书轻量同步：

- 将本地开发文档导入飞书云文档
- 将导入后的文档移动到共享文件夹的对应子目录
- 将飞书文档链接写回 `toLink开发进度表` 的多维表格记录
- 检查固定共享文件夹、Wiki、多维表格入口

它不是强制交付阶段。小需求、临时 bugfix、没有正式文档的改动，默认不做飞书同步。

## 必读资源

开始前读取：

1. `AGENTS.md`
2. `project_info.md`
3. `.agents/skills/lark-sync/lark_resources.md`

如果用户指定了某个模块期次目录，再读取：

4. `docs/模块开发文档/<模块名称>/<phase>/feature_info.md`
5. 该目录下需要同步的本地文档

## 默认同步路径

自动同步默认使用本地 Markdown 解析脚本 + Lark MCP，不走浏览器转换：

1. 读取本地 Markdown 原文。
2. 使用 `.agents/skills/lark-sync/scripts/markdown_to_lark_docx.mjs` 在目标共享文件夹下创建飞书文档，并按表格内容自适应列宽。
3. 脚本在本地解析标题、段落、列表、引用、代码块和表格，飞书侧只接收必要的 MCP block 请求。
4. 通过 Lark MCP 获取文档链接并回写多维表格。
5. 回读目标文件夹和多维表格记录完成校验。

这个路径是默认路径，因为它对长文档和多表格文档更省 token、更稳定。不要把 `markdowntorichtext.com` 生成的整段 HTML 再交给 MCP 转 block 后拆写；该 HTML 往往远大于原 Markdown，且 `docx.v1.document.convert` 返回的大量 block 仍需要二次拆分写入，整体更慢、更消耗上下文。

## 浏览器富文本转换路径

仅在用户明确要求试验网页转换效果、准备人工粘贴到飞书/Word/Notion，或本地脚本无法满足排版诉求时，才使用浏览器访问 `markdowntorichtext.com`：

1. 打开 `https://markdowntorichtext.com/zh/`。
2. 将本地 Markdown 原文粘贴到 Markdown 输入区。
3. 使用页面的 Rich Text / HTML 复制能力获取富文本内容。
4. 如需留痕，将获取到的富文本或 HTML 保存到本地临时文件，例如 `/tmp/lark-sync-<slug>.html`。

浏览器只负责转换和预览；不得用浏览器操作飞书正文粘贴作为默认路径。飞书侧创建文档、归档、权限、链接回写和回读校验必须走 MCP。

只有在以下场景才从默认脚本路径切换到浏览器富文本路径：

- 用户明确要求查看网页转换后的富文本效果。
- 用户准备自己手动调整或粘贴富文本。
- 本地脚本生成的结构无法满足当前文档排版要求，且用户接受额外耗时。

## 默认脚本

自动同步 Markdown 文档时，使用：

```text
.agents/skills/lark-sync/scripts/markdown_to_lark_docx.mjs
```

脚本能力：

- 将 Markdown 标题、段落、引用、列表、分割线转换为飞书新版文档 block。
- 将 fenced code 转换为飞书代码块，自动识别常见语言；`mermaid` 暂按 Markdown 代码块保留源码并开启自动换行。
- 将 Markdown 表格转换为飞书原生 `Table` block，并写入每个单元格。
- 按列逐次调整表格宽度。
- 超过飞书单次创建行数限制的长表格，先创建初始表格再追加行，保持单张原生表格结构。
- 默认只创建飞书文档；只有传入 `--update-table` 才写回多维表格。
- 文档标题和表格链接显示文本应优先保留模块、期次和文档类型等有效信息，不要额外添加 `ToLink Service`、`toLink` 等固定项目前缀，避免挤占多维表格展示宽度。
- 调试时可传入 `--delete-after`，创建后立即删除到回收站，用于验证耗时和结构。
- 默认按表格列内容长度自适应列宽；快速模式可传入 `--skip-widths`，保留表格结构和单元格内容，但跳过列宽调整，后续由人工在飞书中微调表格。

## 触发与跳过

只有满足以下任一条件才进入同步：

- 用户明确要求同步飞书、归档文档、填写进度表或更新 Lark 表格
- 用户给出 Lark 文档、Wiki、多维表格或共享文件夹链接并要求检查
- 当前模块已在 `timeline` 或需求表中存在记录，且用户希望补齐文档链接
- L2/L3 功能需要团队共享查看，且用户确认要同步

以下情况默认跳过：

- L1 小改动
- 临时 bugfix
- 本地文档只是过程草稿
- 用户没有要求同步飞书
- 无法可靠匹配到目标模块记录，且用户未确认新建记录

## 核心原则

- 默认直接执行同步，不要求用户二次确认；只有遇到红旗信号时才暂停确认。
- 不覆盖已有非空链接，除非用户明确允许。
- 能更新已有记录就不新建记录。
- 写入后必须回读校验。
- 只同步文档链接和必要进度元数据，不把本地文档全文塞进表格字段。
- 共享文件夹、表格、字段结构以 `lark_resources.md` 和实时 MCP 查询为准。
- 保留本地 Markdown 的文档结构。不得为了“美化”把原表格改写成列表、段落或其他结构，除非用户明确要求。

## 文档类型映射

| 本地文档 | 目标共享文件夹 | `timeline` 字段 |
| --- | --- | --- |
| `requirement.md` | 需求分析文档 | 需求文档 |
| PRD / 产品说明 | 产品文档 | 需求文档 |
| `technical_design.md` | 技术实现文档 | 技术文档 |
| `implementation_report.md` | 技术实现文档 | 技术文档 |
| `testing_delivery.md` | 测试文档 | 测试文档 |
| 接口说明文档 | 接口文档 | 接口文档 |
| 核心难点、架构说明、一致性方案 | 核心功能设计 | 技术文档或重难点模块分析 |
| 博客、复盘、总结 | 博客 | 不默认写入 `timeline` |

## 执行流程

### 1. 读取固定入口

读取 `.agents/skills/lark-sync/lark_resources.md`，获取：

- 共享文件夹根目录和子目录 token
- `toLink开发进度表` app token
- `timeline`、`需求新增表`、`bugfix`、`重难点模块分析` 等 table_id

### 2. 扫描本地文档

如果用户未指定文件，优先扫描当前模块期次目录：

```text
docs/模块开发文档/<模块名称>/<phase>/
```

只纳入明确可同步的 Markdown 文档。忽略草稿、临时文件、空文件和未完成说明。

### 3. 查询 Lark 现状

写入前必须只读查询：

- 目标共享文件夹内容
- 多维表格字段列表
- 可能匹配的现有记录

匹配维度优先级：

1. 模块名 + 期次
2. 文档标题或链接字段
3. `feature_info.md` 中的模块名称、期次、复杂度和状态
4. 用户明确指定的 record_id

### 4. 形成执行计划

写入前先在内部形成执行计划，并在需要时用简短进展告知用户。若没有红旗信号，不需要等待用户确认，直接进入执行。

计划内容应包含：

```text
待同步文档：
- requirement.md -> 需求分析文档 -> timeline.需求文档
- technical_design.md -> 技术实现文档 -> timeline.技术文档

匹配记录：
- 表：timeline
- record_id：xxx
- 模块：文档上传+查询
- 排期：二期

不会覆盖：
- 已存在非空链接字段：xxx
```

如果用户明确要求 dry-run、只看计划或不要写入，则输出计划后停止。

### 5. Markdown 转飞书文档

同步 Markdown 时默认走本地解析脚本 + MCP 写入路径：

- 表格较多、列较宽，或用户关注表格结构保留时，使用 `scripts/markdown_to_lark_docx.mjs` 生成飞书文档：
  - `docx.v1.document.create` 在目标共享文件夹下创建文档。
  - 将 Markdown 标题、段落、引用、列表、分割线分别转换为对应的 docx block。
  - 将 Markdown 表格转换为飞书原生 `Table` block，表头行单元格加粗。
  - 表格创建后读取自动生成的 `TableCell` 子块，再批量写入单元格文本。
  - 默认按每列表头和正文内容量估算列宽，让短列收窄、长列展开。
  - 如需优先速度，可传入 `--skip-widths` 跳过列宽调整。
  - 如需调整列宽，使用 `docx.v1.documentBlock.batchUpdate` 的 `update_table_property` 按列逐次更新；不要把多列宽度合并到同一个 batch 请求中。
  - 列宽更新失败时只降级为原生表格默认宽度，不改变原表格结构。
- 无表格或表格很少且格式简单时，可以使用 `docx_builtin_import` 导入。
- 只有用户明确要求试验网页转换效果时，才打开 `https://markdowntorichtext.com/zh/` 获取富文本预览或临时 HTML。不要把网页生成的大体积 HTML 作为自动 MCP 写入的默认输入。

转换约束：

- 原 Markdown 一级标题可作为飞书文档标题，正文从其后的块开始。
- 内联代码、加粗等基础样式尽量转为富文本样式。
- Mermaid fenced code 默认转为飞书代码块。当前 Lark MCP 暴露的 docx block API 不能直接创建 `Diagram`，board API 也只暴露了节点列表，尚不能通过 MCP 创建画板 PlantUML/Mermaid 节点；待 MCP 支持 `board.v1.whiteboardNode.createPlantuml` 后再升级为自动文本绘图。
- 表格内容保持原单元格语义，不能为了换行或排版拆成多条列表。
- 如果 block API 不可用，再使用 `docx_builtin_import` 作为最终降级方案，并在结果中说明表格排版可能受飞书 Markdown 导入器限制。

### 6. 执行写入

无红旗信号时按顺序执行：

1. 使用 `scripts/markdown_to_lark_docx.mjs` 和 Lark MCP 写入飞书文档，默认启用表格自适应列宽。
2. 仅在用户显式要求时，额外使用浏览器获取富文本/HTML 作为人工检查材料。
3. 若用户要求浏览器转换，保存临时 HTML 只作为检查或人工粘贴材料，不作为自动 MCP 写入的默认输入。
4. 若导入工具未直接放入目标目录，使用 Drive 文件工具将文档移动到目标子文件夹。
5. 使用多维表格 update/create 工具写入链接字段。
   - 写入表格的飞书文档链接统一使用复制链接口径：`https://wcnnpvbxd7li.feishu.cn/docx/<token>?from=from_copylink`。
   - 若目标字段是 URL 字段，写入 `{ text, link }`。
   - 若目标字段是多行文本，Base update API 只能写字符串；写入飞书文档 URL 字符串，不能直接构造现有表格中 UI 粘贴生成的 Docx mention 富文本对象。
   - 若发现已上传文档标题带有无效前缀，优先用 `drive.v1.file.copy` 复制为短标题文档并回写新链接；不要为了改标题重跑 Markdown 转换。
6. 回读文件夹和表格记录确认写入结果。

如果当前会话没有暴露 Drive 工具，先提示需要 reload MCP；不要改用不透明方式绕过确认。

### 7. 输出结果

最终输出：

- 成功导入的飞书文档链接
- 移动到的共享文件夹
- 更新的表、字段、record_id
- 未同步的文档和原因
- 需要人工处理的冲突或缺失权限

## 红旗信号

遇到以下情况必须暂停并询问用户：

- 本地文档无法确定对应模块或期次
- Lark 中存在多条可能匹配的记录
- 目标字段已有非空链接
- 需要新建多维表格记录
- Drive 移动失败但文档已导入
- 写入权限、用户授权或 MCP 工具缺失
