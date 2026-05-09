# Lark 固定资源入口

## 1. 文档定位

本文件记录 ToLink 项目在 Lark / 飞书中的固定协作资源入口，供 `lark-sync` skill 使用。

本文件只记录入口和稳定标识，不复制 Lark 中的动态数据内容。

## 2. 共享文件夹

### 2.1 归档根目录

| 名称 | 链接 | folder_token |
| --- | --- | --- |
| ToLink 共享归档根目录 | https://wcnnpvbxd7li.feishu.cn/drive/folder/J7GzfgFYzllKEcdNTejcggd5nuf | `J7GzfgFYzllKEcdNTejcggd5nuf` |

### 2.2 子目录映射

| 子目录 | folder_token | 默认用途 |
| --- | --- | --- |
| 博客 | `ZAzxf5KF1lSoX0dIhrBcn166n6b` | 博客、复盘、总结 |
| 需求分析文档 | `Y26ifM1XalTDnPdzqQ4c91CmnQd` | `requirement.md`、需求分析 |
| 产品文档 | `F7p6f32aUlTHGPd6JE0ctk2qn8x` | PRD、产品说明 |
| 核心功能设计 | `Q5GYfRmcOlSpytdu6AIcvOWhn5u` | 架构、难点、一致性方案 |
| 技术实现文档 | `VNekfOkjIljzTGdVmaYcZDxZn7f` | `technical_design.md`、`implementation_report.md` |
| 测试文档 | `AogtfNEVDlxTlHdRNTLcFP4wnSd` | `testing_delivery.md` |
| 接口文档 | `MOZRfO5puljfACdm2B3c1bNongb` | 接口说明、API 文档 |

## 3. 多维表格

### 3.1 toLink开发进度表

| 名称 | 类型 | 链接 | 关键标识 |
| --- | --- | --- | --- |
| toLink开发进度表 | Wiki 多维表格 | https://wcnnpvbxd7li.feishu.cn/wiki/S2HiwIT78i5HH2kmOjZcFKdInl2 | `wiki_node_token=S2HiwIT78i5HH2kmOjZcFKdInl2`，`app_token=YOOcbHLOZaAt0JsJMr9cM8iknuh` |

- Wiki 节点：`S2HiwIT78i5HH2kmOjZcFKdInl2`
- Wiki 空间：`7618974002324704448`
- Base App Token：`YOOcbHLOZaAt0JsJMr9cM8iknuh`
- 对象类型：`bitable`

### 3.2 数据表

| 表名 | table_id | 用途备注 |
| --- | --- | --- |
| timeline | `tblUkirtngZuNWYZ` | 项目时间线 |
| 需求新增表 | `tblA6Hz0oOeTckMS` | 新需求记录 |
| bugfix | `tblCiS2uBZayOXYm` | Bug 修复记录 |
| LinkNote设计意见征集表 | `tbltyU4adcqafzdT` | 设计意见征集 |
| 开销 | `tblCaJ2ChtG7GDJw` | 开销记录 |
| 重难点模块分析 | `tblY4E3nmLuupLNC` | 重难点模块分析 |

### 3.3 字段口径

`timeline` 使用自关联字段组织层级。ToLink Java 服务相关本地文档不要直接靠 `项目` 文本新建大类，而应挂到现有 Java 父记录下：

| 本地/口语名称 | `timeline` 归属方式 |
| --- | --- |
| ToLink Service / tolink-service / tolinkrag（Java） | 新建模块行时设置 `父记录=rec27aSdzqVRrH` |

其中 `rec27aSdzqVRrH` 是现有 `LinkRag(Java)` 板块下的父级记录（模块：`用户信息管理+LLM参数配置`，项目：`LinkRag(Java)`）。同步本仓库文档时不要新建或填写 `ToLink Service` 等新的项目大类；子模块行通常保持 `项目` 为空，通过 `父记录` 归入 Java 板块。

## 4. MCP 能力要求

`lark-user` MCP 需要至少可用：

- `docx_builtin_import`
- `docx_v1_document_create`
- `docx_v1_documentBlockChildren_create`
- `docx_v1_documentBlock_batchUpdate`
- `docx_v1_documentBlock_list`
- `bitable_v1_appTableField_list`
- `bitable_v1_appTableRecord_search`
- `bitable_v1_appTableRecord_update`
- `bitable_v1_appTableRecord_create`
- `drive_v1_file_list`
- `drive_v1_file_move`
- `drive_v1_meta_batchQuery`
- `drive_v1_file_delete`（仅用于清理调试文档）
