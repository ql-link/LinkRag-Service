# 博客模块

本文档描述博客文章、Markdown 正文、图片资源和公开访问的实现边界。

## 模块边界

代码入口：

| 职责 | 入口 |
| --- | --- |
| 文章管理 | `BlogPostServiceImpl` |
| Markdown 正文存储 | `BlogContentStorageServiceImpl` |
| 图片与封面资源 | `BlogAssetServiceImpl` |
| 管理端 HTTP | `BlogAdminController` |
| 公开 HTTP | `BlogPublicController` |

核心表：

| 表 | 作用 |
| --- | --- |
| `blog_post` | 文章元信息、发布状态、正文对象 key |
| `blog_asset` | 封面、正文图片等资源对象 key |

## 正文存储

博客正文不直接保存在 MySQL。`blog_post.content_object_key` 指向 PUBLIC OSS 中的 Markdown 对象。

正文保存路径形如：

```text
blog/{postId}/content/{uuid}.md
```

保存或导入 Markdown 时会上传新的 PUBLIC 对象，并在数据库中切换 `content_object_key`。旧正文对象在切换后按 PUBLIC 位置删除。

## 图片处理

图片资源统一保存到 PUBLIC OSS：

| 类型 | 典型路径 |
| --- | --- |
| 封面 | `blog/{postId}/cover/...` |
| 正文图片 | `blog/{postId}/images/...` |

Markdown 导入或保存时，远程 HTTP 图片和 data URL 图片会被下载或解码后上传到 PUBLIC OSS，并把 Markdown 中的引用改写为公开 URL。相对本地路径会被拒绝。

删除正文图片前会检查当前 Markdown 是否仍引用该资源；仍被引用时拒绝删除，避免公开文章出现断图。

## 状态流转

文章状态由 `BlogPostStatus` 控制：

| 状态 | 含义 |
| --- | --- |
| `DRAFT` | 草稿，仅管理端可见 |
| `PUBLISHED` | 已发布，公开接口可见 |

发布要求正文存在。删除为软删除，并尽力清理正文和资源对象。

## 接口边界

管理端接口在 `/api/v1/admin/blog` 下，负责文章 CRUD、正文导入/保存、发布/下架、资源管理。

公开接口在 `/api/v1/blog` 下，只返回已发布文章。公开详情读取 `content_object_key` 对应的 PUBLIC Markdown 对象并返回正文内容。

## 修改注意事项

- 不要把博客正文改回 MySQL 大字段。
- 不要把博客正文或图片保存到 PRIVATE OSS；公开文章依赖 PUBLIC URL。
- 新增资源类型时需同步 `BlogAssetType`、OSS 路径规则和删除约束。
- API 变更同步 `docs/api/api_contracts.md`，表结构变更同步 `docs/api/mysql_schema.md`。
