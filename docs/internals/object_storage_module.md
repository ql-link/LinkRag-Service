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

## 博客对象规则

博客模块复用 `IOssService`，不新增或修改 OSS 组件接口：

| 对象 | Key | 可见性 | 说明 |
| --- | --- | --- | --- |
| Markdown 正文 | `blog/{postId}/content/{uuid}.md` | `PRIVATE` | 上传新对象成功后切换 `blog_post.content_object_key`，避免覆盖线上正文 |
| 封面图片 | `blog/{postId}/cover/{uuid}.{suffix}` | `PUBLIC` | `blog_asset.public_url` 供公开端展示 |
| 正文图片 | `blog/{postId}/content/{uuid}.{suffix}` | `PUBLIC` | Markdown 直接引用公开 URL |

- Markdown 上传使用 `upload2PreviewUrl(PRIVATE, File, "text/markdown", key)`；读取使用 `downloadFile(PRIVATE, key, temp)`。
- 图片上传使用 `upload2PreviewUrl(PUBLIC, MultipartFile, key)`。
- 文章和资源删除均为数据库软删，不调用 `deleteFile`。
- MinIO 与 MySQL 不可同事务；DB 写失败可能留下孤儿 UUID 对象，后续可按 `blog/` 前缀对账清理。
- MinIO 的 PUBLIC bucket 或公开前缀必须具备匿名读策略，否则已生成的 `public_url` 仍会访问失败。
