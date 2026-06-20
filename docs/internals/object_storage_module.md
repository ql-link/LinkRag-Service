# Object Storage Module

## 职责

OSS 组件位于 `link-components/toLink-components-oss`，业务上传入口位于 `OssApplicationService` 和文档文件服务。

## 当前能力

OSS 组件（`link-components/toLink-components-oss`）提供存储能力：`IOssService` / `LocalFileService` / `MinioFileService` / `PrivateFileResolver`。业务门面与对象 key/规则（`OssApplicationService` / `OssObjectKeyGenerator` / `OssUploadRuleRegistry`）位于 `link-service/.../service` 与 `service/oss`，不在组件内。

- `IOssService`：统一对象存储接口，提供两种上传重载：
  - `upload2PreviewUrl(place, MultipartFile, key)`：从请求期 `MultipartFile` 上传（同步入口）。
  - `upload2PreviewUrl(place, File, contentType, key)`：从已物化的本地 `File` 上传，供请求外/异步场景使用（如文档上传异步化在线程池里上传）——请求期 `MultipartFile` 在请求结束后不可再读，故异步链路改传本地文件 + 显式 `contentType`。
  - `resolvePublicUrl(place, objectKey)`：由 object key 拼出匿名可读的公开 URL，供"只存 key、用时拼 URL"的场景使用（如反馈附件）。
- `LocalFileService`：本地文件实现（`File` 重载用 `Files.copy`）。
- `MinioFileService`：MinIO 实现（`File` 重载用 `putObject` + 本地文件流 + `localFile.length()` + `contentType`）。
- `PrivateFileResolver`：私有对象本地解析与缓存。
- `OssObjectKeyGenerator`、`OssUploadRuleRegistry`：业务对象 key 与上传规则。
- `OssApplicationService`：业务上传门面。`upload(bizType, file)` 返回 preview 值（公开桶=URL、私有桶=key）；`uploadAndDescribe(bizType, file)` 返回 `UploadResult{objectKey, previewUrl}`，供需要持有 object key 的调用方使用。

## 约定

新增上传业务需明确 public/private 边界、对象 key 规则、访问 URL 和删除策略。异步/请求外上传须用 `File` 重载（不要持有请求期 `MultipartFile`）。

- **删除策略（隐性删除）**：数据集 / 文档文件删除采用软删保留原文件，**不调用 `IOssService.deleteFile` 物理删 OSS 原文件对象**；原文件随软删行保留，便于追溯 / 恢复。Python 侧衍生产物（清洗文件、向量等）的删除交 Python（删除通知 MQ 占位、未实现）。

## Feedback 对象规则

反馈附件按非敏感数据写入公开桶 `tolink-public`（`OssSavePlaceEnum.PUBLIC`）。数据库仍只保存 `user_feedback.attachment_object_key`（方案甲：只存 object key，不存 bucket 或完整 URL）；可访问 URL 由后端用 `IOssService.resolvePublicUrl(PUBLIC, objectKey)` 按需拼装，写入 `FeedbackDTO.attachmentUrl` 返回给前端/管理端。

| 对象 | Key | 枚举值 | 说明 |
| --- | --- | --- | --- |
| 反馈附件 | `feedback/{yyyy}/{MM}/{uuid}.{suffix}` | `PUBLIC` | 匿名反馈可选附件，允许图片与常见文档后缀，路径精度到月；只把 object key 写入 `user_feedback`，URL 用时拼装 |

- `OssUploadRuleRegistry` 中 `feedback` 规则使用公开存储 `PUBLIC`，默认大小上限 10MB。
- `OssObjectKeyGenerator` 对 `feedback` 使用 `yyyy/MM` 月级目录，避免单目录对象过多。
- 反馈侧通过 `OssApplicationService.uploadAndDescribe("feedback", file)` 取回 `UploadResult.objectKey()` 落库——公开桶上传返回的是完整 URL，故不能直接存返回值。
- 匿名反馈入库失败时，Java 端调用 `deleteFile(PUBLIC, attachmentObjectKey)` 删除刚上传的附件；因字段存的是 object key，删除直接定位对象，不需从 URL 反解。删除失败只记录日志，不掩盖原始入库异常。

## 桶命名规范

三桶已收敛为两桶（一私一公）：

| 桶名 | 用途 | 访问策略 |
| --- | --- | --- |
| `tolink-rag-docs` | RAG 知识库原始文档（私有） | 无匿名访问 |
| `tolink-public` | 所有不敏感资源：博客图片/Markdown 正文 + 反馈附件 | 匿名读（anonymous download） |

> 原博客专用桶 `tolink-blog` 与从未落地的 `PUBLIC` 占位桶已合并为单一公开桶 `tolink-public`；存量博客对象不迁移，旧桶 `tolink-blog` 待服务稳定后由运维删除。

`OssSavePlaceEnum` 枚举值与桶的对应关系（仅两值）：

| 枚举值 | 目标桶 | 返回值 |
| --- | --- | --- |
| `PRIVATE` | `tolink-rag-docs` | 对象 key（字符串路径，私有桶不可匿名访问） |
| `PUBLIC` | `tolink-public` | 完整公开 URL（匿名可读） |

## 博客对象规则

博客模块统一使用 `OssSavePlaceEnum.PUBLIC`，所有对象写入 `tolink-public` 桶，按三个子目录区分类型（路径前缀不变）：

| 对象 | Key | 枚举值 | 说明 |
| --- | --- | --- | --- |
| Markdown 正文 | `blog/{postId}/content/{uuid}.md` | `PUBLIC` | 上传成功后切换 `blog_post.content_object_key`；调用方只用返回的 URL 做非空校验，实际存库的是上传前生成的 objectKey |
| 封面图片 | `blog/{postId}/cover/{uuid}.{suffix}` | `PUBLIC` | `blog_asset.public_url` 供公开端展示 |
| 正文图片 | `blog/{postId}/images/{uuid}.{suffix}` | `PUBLIC` | 编辑器上传、粘贴或 Markdown 导入/保存时写入，Markdown 直接引用公开 URL |

- 所有博客对象统一使用 `OssSavePlaceEnum.PUBLIC`，路由至 `tolink-public` 桶，返回完整公开 URL。
- Markdown 导入/保存使用 `upload2PreviewUrl(PUBLIC, File, "text/markdown", key)`；读取使用 `downloadFile(PUBLIC, key, temp)`。
- 封面图片上传使用 `upload2PreviewUrl(PUBLIC, MultipartFile, key)`，资源类型为 `COVER`。
- 正文图片上传使用 `upload2PreviewUrl(PUBLIC, MultipartFile, key)`，资源类型为 `CONTENT_IMAGE`，响应返回可插入编辑器的 Markdown 图片片段。
- Markdown 导入/保存会扫描 `![](...)` 图片引用，支持 `http` / `https` 远端图片和 `data:image/*;base64` 内联图片；成功下载或解码后写入 `tolink-public` 桶、记录 `blog_asset.CONTENT_IMAGE`，并将 Markdown 图片地址改写为公开 URL。网络图片失败或被限制拦截时保留原 URL；本地相对路径图片会被拒绝。
- 文章删除为数据库软删，不批量删除 MinIO 对象。资源删除为数据库软删；正文图片仍被当前 Markdown 引用时拒绝删除，允许删除时同步调用 `deleteFile(PUBLIC, objectKey)` 删除 `tolink-public` 对象。
- MinIO 与 MySQL 不可同事务；DB 写失败可能留下孤儿 UUID 对象，后续可按 `blog/` 前缀对账清理。
- `tolink-public` 桶必须配置匿名读策略（`mc anonymous set download`），否则已生成的 `public_url` 仍会访问失败。
