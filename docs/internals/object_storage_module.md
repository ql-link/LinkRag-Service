# Object Storage Module

## 职责

OSS 组件位于 `link-components/toLink-components-oss`，业务上传入口位于 `OssApplicationService` 和文档文件服务。

## 当前能力

- `IOssService`：统一对象存储接口，提供两种上传重载：
  - `upload2PreviewUrl(place, MultipartFile, key)`：从请求期 `MultipartFile` 上传（同步入口）。
  - `upload2PreviewUrl(place, File, contentType, key)`：从已物化的本地 `File` 上传，供请求外/异步场景使用（如文档上传异步化在线程池里上传）——请求期 `MultipartFile` 在请求结束后不可再读，故异步链路改传本地文件 + 显式 `contentType`。
- `LocalFileService`：本地文件实现（`File` 重载用 `Files.copy`）。
- `MinioFileService`：MinIO 实现（`File` 重载用 `putObject` + 本地文件流 + `localFile.length()` + `contentType`）。
- `PrivateFileResolver`：私有对象本地解析与缓存。
- `OssObjectKeyGenerator`、`OssUploadRuleRegistry`：业务对象 key 与上传规则。

## 约定

新增上传业务需明确 public/private 边界、对象 key 规则、访问 URL 和删除策略。异步/请求外上传须用 `File` 重载（不要持有请求期 `MultipartFile`）。

- **删除策略（隐性删除）**：数据集 / 文档文件删除采用软删保留原文件，**不调用 `IOssService.deleteFile` 物理删 OSS 原文件对象**；原文件随软删行保留，便于追溯 / 恢复。Python 侧衍生产物（清洗文件、向量等）的删除交 Python（删除通知 MQ 占位、未实现）。

## 桶命名规范

| 桶名 | 用途 | 访问策略 |
| --- | --- | --- |
| `tolink-rag-docs` | RAG 知识库原始文档（私有） | 无匿名访问 |
| `tolink-blog` | 博客专用桶（图片 + Markdown 正文） | 匿名读（anonymous download） |

`OssSavePlaceEnum` 枚举值与桶的对应关系：

| 枚举值 | 目标桶 | 返回值 |
| --- | --- | --- |
| `PRIVATE` | `tolink-rag-docs` | 对象 key（字符串路径） |
| `BLOG` | `tolink-blog` | 完整公开 URL |
| `PUBLIC` | 暂未配置（历史保留，avatar/chatImage 路由占位） | — |

## 博客对象规则

博客模块统一使用 `OssSavePlaceEnum.BLOG`，所有对象写入 `tolink-blog` 桶，按三个子目录区分类型：

| 对象 | Key | 枚举值 | 说明 |
| --- | --- | --- | --- |
| Markdown 正文 | `blog/{postId}/content/{uuid}.md` | `BLOG` | 上传成功后切换 `blog_post.content_object_key`；调用方只用返回的 URL 做非空校验，实际存库的是上传前生成的 objectKey |
| 封面图片 | `blog/{postId}/cover/{uuid}.{suffix}` | `BLOG` | `blog_asset.public_url` 供公开端展示 |
| 正文图片 | `blog/{postId}/images/{uuid}.{suffix}` | `BLOG` | 编辑器上传、粘贴或 Markdown 导入/保存时写入，Markdown 直接引用公开 URL |

- 所有博客对象统一使用 `OssSavePlaceEnum.BLOG`，路由至 `tolink-blog` 桶，返回完整公开 URL。
- Markdown 导入/保存使用 `upload2PreviewUrl(BLOG, File, "text/markdown", key)`；读取使用 `downloadFile(BLOG, key, temp)`。
- 封面图片上传使用 `upload2PreviewUrl(BLOG, MultipartFile, key)`，资源类型为 `COVER`。
- 正文图片上传使用 `upload2PreviewUrl(BLOG, MultipartFile, key)`，资源类型为 `CONTENT_IMAGE`，响应返回可插入编辑器的 Markdown 图片片段。
- Markdown 导入/保存会扫描 `![](...)` 图片引用，支持 `http` / `https` 远端图片和 `data:image/*;base64` 内联图片；成功下载或解码后写入 `tolink-blog` 桶、记录 `blog_asset.CONTENT_IMAGE`，并将 Markdown 图片地址改写为公开 URL。网络图片失败或被限制拦截时保留原 URL；本地相对路径图片会被拒绝。
- 文章删除为数据库软删，不批量删除 MinIO 对象。资源删除为数据库软删；正文图片仍被当前 Markdown 引用时拒绝删除，允许删除时同步调用 `deleteFile(BLOG, objectKey)` 删除 `tolink-blog` 对象。
- MinIO 与 MySQL 不可同事务；DB 写失败可能留下孤儿 UUID 对象，后续可按 `blog/` 前缀对账清理。
- `tolink-blog` 桶必须配置匿名读策略（`mc anonymous set download`），否则已生成的 `public_url` 仍会访问失败。
