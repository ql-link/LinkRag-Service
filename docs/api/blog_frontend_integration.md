# Blog Frontend Integration

本文档面向前端联调博客模块。接口事实来源为：

- `BlogAdminController`
- `BlogPublicController`
- `BlogPostServiceImpl`
- `BlogAssetServiceImpl`
- `BlogContentStorageServiceImpl`

## 1. 基础约定

### 1.1 Base URL

```text
/api/v1
```

### 1.2 统一响应

成功：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

失败：

```json
{
  "code": 40001,
  "message": "slug格式不合法",
  "data": null
}
```

### 1.3 分页响应

分页数据包在 `data` 内：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "total": 0,
    "page": 1,
    "pageSize": 20,
    "totalPages": 0
  }
}
```

### 1.4 鉴权 Header

当前 Sa-Token 配置：

```yaml
sa-token:
  token-name: satoken
```

管理端接口请求需要携带：

```http
satoken: <accessToken>
```

登录接口 `POST /api/v1/auth/login` 返回 `accessToken`。虽然响应中的 `tokenType` 当前是 `Bearer`，但服务端配置的 token 名是 `satoken`，前端联调博客管理接口时以 `satoken` Header 为准。

公开端接口无需登录。

## 2. 管理员判断方式

博客管理接口通过服务端角色判断，不由前端传角色字段决定。

完整链路：

1. 用户登录后，Sa-Token 记录登录 ID，即 `sys_user.id`。
2. 前端调用管理端接口时带 `satoken`。
3. `SaTokenAnnotationConfig` 注册 `SaInterceptor().isAnnotation(true)`，让 Controller 上的 Sa-Token 注解生效。
4. `BlogAdminController` 类上标注 `@SaCheckRole("ADMIN")`。
5. Sa-Token 调用 `StpInterfaceImpl#getRoleList(loginId, loginType)` 查询当前用户角色。
6. `StpInterfaceImpl` 先按 userId 从 `UserCacheService` 取用户资料；缓存未命中时回源 `sys_user` 表。
7. 取 `sys_user.role` 作为角色列表返回。
8. 只有角色包含字符串 `ADMIN` 时，请求才会进入博客管理接口。

前端登录后可调用 `GET /api/v1/user/profile`，根据响应中的 `data.role === "ADMIN"` 决定是否展示博客管理入口。但这只用于界面展示，真正的权限控制仍由服务端 `@SaCheckRole("ADMIN")` 完成，前端不能通过自行修改 role 绕过。

结果：

| 情况 | HTTP | 响应 |
| --- | --- | --- |
| 未登录 / token 失效 | 401 | `{ "code": 401, "message": "未登录或登录已过期", "data": null }` |
| 已登录但不是 ADMIN | 403 | `{ "code": 403, "message": "权限不足", "data": null }` |
| ADMIN | 继续执行业务接口 | - |

用户角色合法值：

```text
ADMIN
USER
```

普通用户 `USER` 不能创建、编辑、上传正文、上传图片、发布、下架或删除博客文章。

## 3. 字段模型

### 3.1 BlogPostAdminListDTO

管理端列表项，不包含 Markdown 正文。

```json
{
  "id": 10000,
  "title": "第一篇博客",
  "slug": "first-post",
  "summary": "摘要",
  "contentObjectKey": "blog/10000/content/xxxxxxxx.md",
  "coverAssetId": 20000,
  "status": "DRAFT",
  "publishedAt": null,
  "createdBy": 1,
  "createdAt": "2026-06-08T16:00:00",
  "updatedAt": "2026-06-08T16:00:00"
}
```

### 3.2 BlogPostAdminDetailDTO

管理端详情，包含 Markdown 正文。

```json
{
  "id": 10000,
  "title": "第一篇博客",
  "slug": "first-post",
  "summary": "摘要",
  "contentObjectKey": "blog/10000/content/xxxxxxxx.md",
  "contentMarkdown": "# 标题\n正文",
  "coverAssetId": 20000,
  "status": "PUBLISHED",
  "publishedAt": "2026-06-08T16:10:00",
  "createdBy": 1,
  "createdAt": "2026-06-08T16:00:00",
  "updatedAt": "2026-06-08T16:10:00"
}
```

### 3.3 BlogPostPublicListDTO

公开列表项，不包含 Markdown 正文。

```json
{
  "id": 10000,
  "title": "第一篇博客",
  "slug": "first-post",
  "summary": "摘要",
  "coverAssetId": 20000,
  "publishedAt": "2026-06-08T16:10:00"
}
```

### 3.4 BlogPostPublicDetailDTO

公开详情，包含 Markdown 正文。

```json
{
  "id": 10000,
  "title": "第一篇博客",
  "slug": "first-post",
  "summary": "摘要",
  "coverAssetId": 20000,
  "publishedAt": "2026-06-08T16:10:00",
  "contentMarkdown": "# 标题\n正文"
}
```

### 3.5 BlogAssetDTO

```json
{
  "id": 20000,
  "postId": 10000,
  "assetType": "CONTENT_IMAGE",
  "originalFilename": "image.png",
  "contentType": "image/png",
  "fileSize": 12345,
  "objectKey": "blog/10000/content/xxxxxxxx.png",
  "publicUrl": "https://oss.example.com/blog/10000/content/xxxxxxxx.png",
  "createdBy": 1,
  "createdAt": "2026-06-08T16:05:00",
  "updatedAt": "2026-06-08T16:05:00"
}
```

## 4. 枚举与参数限制

### 4.1 文章状态

```text
DRAFT
PUBLISHED
```

### 4.2 资源类型

```text
COVER
CONTENT_IMAGE
```

`assetType` 后端会做大小写归一，但前端应固定传大写。

### 4.3 slug 规则

`slug` 是公开访问标识，对应公开详情路径：

```text
GET /api/v1/blog/posts/{slug}
```

规则：

- 只允许小写字母、数字、中划线。
- 必须以小写字母或数字开头。
- 必须以小写字母或数字结尾。
- 长度 3 到 100。
- 示例合法值：`first-post`、`blog2026`。
- 示例非法值：`FirstPost`、`first_post`、`-first`、`first-`。

### 4.4 文件格式

Markdown 正文：

| 项 | 规则 |
| --- | --- |
| 表单字段名 | `file` |
| 文件后缀 | `.md` / `.markdown` |
| 文本编码 | UTF-8 |
| 空文件 | 不允许 |
| 业务大小限制 | 无 |

图片资源：

| 后缀 | MIME |
| --- | --- |
| `.jpg` / `.jpeg` | `image/jpeg` |
| `.png` | `image/png` |
| `.gif` | `image/gif` |
| `.webp` | `image/webp` |

不支持 SVG。业务层不设大小限制，但请求仍受网关、Servlet multipart、临时磁盘和对象存储基础限制。当前默认 multipart 配置为 20MB。

## 5. 管理端接口

以下接口全部要求 `ADMIN`。

### 5.1 创建草稿

```http
POST /api/v1/admin/blog/posts
Content-Type: application/json
satoken: <accessToken>
```

请求：

```json
{
  "title": "第一篇博客",
  "slug": "first-post",
  "summary": "摘要"
}
```

字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `title` | 是 | 文章标题，最长 255 |
| `slug` | 是 | 公开访问标识，最长 100，规则见 4.3 |
| `summary` | 否 | 摘要，最长 1000 |

响应：`BlogPostAdminDetailDTO`。

新建文章状态固定为 `DRAFT`，此时 `contentMarkdown` 通常为 `null`。

### 5.2 更新文章元数据

```http
PATCH /api/v1/admin/blog/posts/{postId}
Content-Type: application/json
satoken: <accessToken>
```

请求：

```json
{
  "title": "新的标题",
  "slug": "new-slug",
  "summary": "新的摘要",
  "coverAssetId": 20000
}
```

字段均可选，但至少提供一个字段。

注意：

- `coverAssetId` 必须是当前文章下 `assetType=COVER` 的资源。
- 当前接口不支持通过传 `null` 清空摘要或封面。删除当前封面资源时，后端会自动清空文章的 `coverAssetId`。

响应：`BlogPostAdminDetailDTO`。

### 5.3 管理端文章列表

```http
GET /api/v1/admin/blog/posts?page=1&pageSize=20&status=DRAFT
satoken: <accessToken>
```

Query：

| 参数 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `page` | 否 | 1 | 页码 |
| `pageSize` | 否 | 20 | 每页数量 |
| `status` | 否 | - | `DRAFT` / `PUBLISHED` |

响应：`PageResult<BlogPostAdminListDTO>`。

列表不返回 `contentMarkdown`。

### 5.4 管理端文章详情

```http
GET /api/v1/admin/blog/posts/{postId}
satoken: <accessToken>
```

响应：`BlogPostAdminDetailDTO`。

如果文章已有正文，返回 `contentMarkdown`。

### 5.5 上传或替换 Markdown 正文

```http
POST /api/v1/admin/blog/posts/{postId}/content
Content-Type: multipart/form-data
satoken: <accessToken>
```

FormData：

```text
file=<Markdown文件>
```

响应：`BlogPostAdminDetailDTO`。

行为：

- 后端将文件保存为私有 OSS 对象。
- 对象 Key 形如 `blog/{postId}/content/{uuid}.md`。
- 替换正文时，先上传新对象，再更新数据库 `contentObjectKey`。
- 旧 Markdown 对象保留，不物理删除。

### 5.6 上传图片资源

```http
POST /api/v1/admin/blog/posts/{postId}/assets
Content-Type: multipart/form-data
satoken: <accessToken>
```

FormData：

```text
assetType=CONTENT_IMAGE
file=<图片文件>
```

响应：`BlogAssetDTO`。

行为：

- `CONTENT_IMAGE` 用于正文内图片。
- `COVER` 用于封面；上传成功后会自动更新文章 `coverAssetId`。
- 图片保存为公共 OSS 对象，前端使用响应中的 `publicUrl`。

正文图片典型流程：

1. 前端编辑器选择图片。
2. 调用本接口上传，`assetType=CONTENT_IMAGE`。
3. 取响应 `data.publicUrl`。
4. 将 Markdown 插入：

```markdown
![图片说明](https://oss.example.com/blog/10000/content/xxxxxxxx.png)
```

### 5.7 查询文章资源

```http
GET /api/v1/admin/blog/posts/{postId}/assets
satoken: <accessToken>
```

响应：`List<BlogAssetDTO>`。

只返回当前文章下未删除资源。

### 5.8 删除文章资源

```http
DELETE /api/v1/admin/blog/posts/{postId}/assets/{assetId}
satoken: <accessToken>
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

行为：

- 只软删除 `blog_asset` 元数据。
- 不删除 OSS 对象。
- 如果删除的是当前封面资源，会自动清空文章 `coverAssetId`。

### 5.9 发布文章

```http
POST /api/v1/admin/blog/posts/{postId}/publish
satoken: <accessToken>
```

响应：`BlogPostAdminDetailDTO`。

发布前要求：

- 文章存在且未删除。
- 已上传 Markdown 正文。
- Markdown 私有对象可读取。

首次发布会写入 `publishedAt`。下架后再次发布，不会重置首次发布时间。

### 5.10 下架文章

```http
POST /api/v1/admin/blog/posts/{postId}/unpublish
satoken: <accessToken>
```

响应：`BlogPostAdminDetailDTO`。

行为：将状态改回 `DRAFT`，公开端不可见。

### 5.11 删除文章

```http
DELETE /api/v1/admin/blog/posts/{postId}
satoken: <accessToken>
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

行为：

- 软删除文章。
- 不删除 Markdown 对象。
- 不删除图片对象。
- 删除后原 `slug` 可重新创建使用。

## 6. 公开端接口

公开端无需登录，只返回已发布且未删除文章。

### 6.1 公开文章列表

```http
GET /api/v1/blog/posts?page=1&pageSize=20
```

响应：`PageResult<BlogPostPublicListDTO>`。

列表不返回 `contentMarkdown`。

### 6.2 公开文章详情

```http
GET /api/v1/blog/posts/{slug}
```

响应：`BlogPostPublicDetailDTO`。

只允许访问 `PUBLISHED` 文章。草稿、已下架、已删除、slug 不存在均返回 404。

## 7. 常见错误

| HTTP | `code` | `message` 示例 | 场景 |
| --- | --- | --- | --- |
| 400 | 400 | `title: 文章标题不能为空` | Bean Validation 参数失败 |
| 400 | 40001 | `slug格式不合法` | slug 不符合规则 |
| 400 | 40001 | `slug已存在` | slug 与未删除文章冲突 |
| 400 | 40001 | `请至少提供一个需要更新的字段` | PATCH 请求体无有效字段 |
| 400 | 40001 | `正文文件仅支持md或markdown格式` | 正文文件后缀不支持 |
| 400 | 40001 | `Markdown正文必须是有效的UTF-8文本` | Markdown 编码不合法 |
| 400 | 40001 | `图片格式不支持` | 图片后缀不支持 |
| 400 | 40001 | `图片MIME类型不支持` | 图片 MIME 与后缀不匹配或不支持 |
| 400 | 40001 | `请先上传Markdown正文` | 发布前未上传正文 |
| 400 | 40001 | `Markdown正文对象不存在` | 发布时私有正文对象不可读 |
| 401 | 401 | `未登录或登录已过期` | 管理端未携带有效 token |
| 403 | 403 | `权限不足` | 已登录但不是 ADMIN |
| 404 | 404 | `博客文章不存在` | postId 不存在、已删除，或公开 slug 不可见 |
| 404 | 404 | `博客资源不存在` | assetId 不存在或不属于当前文章 |
| 500 | 50002 | `图片上传失败` | OSS 上传失败 |
| 500 | 50003 | `读取Markdown正文失败` | 详情读取私有正文对象失败 |

## 8. 前端推荐流程

### 8.1 新建并发布文章

1. `POST /api/v1/admin/blog/posts` 创建草稿。
2. `POST /api/v1/admin/blog/posts/{postId}/content` 上传 Markdown 正文。
3. 编辑器内图片逐个调用 `POST /api/v1/admin/blog/posts/{postId}/assets`，取 `publicUrl` 写回 Markdown。
4. 如果有封面，调用资源上传接口并传 `assetType=COVER`。
5. `POST /api/v1/admin/blog/posts/{postId}/publish` 发布。

### 8.2 编辑已发布文章

1. `GET /api/v1/admin/blog/posts/{postId}` 获取详情和 `contentMarkdown`。
2. 前端编辑 Markdown。
3. 如正文变化，重新上传 Markdown 文件。
4. 如元数据变化，调用 PATCH 更新。
5. 文章保持 `PUBLISHED` 状态；正文替换后公开详情读取新 Markdown。

### 8.3 公开展示

1. 列表页调用 `GET /api/v1/blog/posts`。
2. 详情页使用列表中的 `slug` 调用 `GET /api/v1/blog/posts/{slug}`。
3. 前端自行渲染 Markdown。
4. Markdown 内图片 URL 已是公共 URL，可直接展示。

## 9. 当前契约限制

- 公开文章列表和详情当前只返回 `coverAssetId`，不直接返回封面 `publicUrl`。如果公开博客页面需要直接展示封面图，建议后端补充 `coverUrl` 字段，或新增公开资源解析能力。
- 不提供 Markdown 下载接口。
- 不支持评论、点赞、收藏、分类、标签、作者展示。
- 文章和资源删除均不物理删除 OSS 对象。
