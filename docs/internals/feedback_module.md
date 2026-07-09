# 用户反馈模块

本文档描述匿名反馈提交、附件存储和管理员处理流程。

## 模块边界

代码入口：

| 职责 | 入口 |
| --- | --- |
| 用户提交 | `FeedbackServiceImpl` |
| 管理端处理 | `AdminFeedbackServiceImpl` |
| 用户 HTTP | `FeedbackController` |
| 管理端 HTTP | `AdminFeedbackController` |

核心表：

| 表 | 作用 |
| --- | --- |
| `user_feedback` | 反馈标题、内容、联系方式、附件 key、处理状态 |

## 用户提交

用户通过 `POST /api/v1/feedback` 以 multipart 表单提交反馈。当前接口不要求登录。

服务端会校验：

- 标题和内容不能为空。
- 反馈类型来自 `FeedbackType`。
- 可选附件通过统一 OSS 应用服务保存。

附件对象保存在 PUBLIC OSS，数据库只保存 object key。返回详情时由服务端根据 object key 解析公开 URL。

如果附件已经上传但数据库写入失败，服务端会尝试清理已上传对象，避免孤儿文件。

## 管理端处理

管理员通过 `/api/v1/admin/feedback` 查询和处理反馈：

| 操作 | 说明 |
| --- | --- |
| 列表 | 支持状态、类型、优先级等过滤 |
| 详情 | 返回反馈内容和附件公开 URL |
| 状态更新 | 更新处理状态，终态写 `processed_at` |
| 优先级更新 | 优先级范围为 1 到 3 |
| 回复 | 写入管理员 ID、回复内容和处理时间 |

状态来自 `FeedbackStatus`。终态处理会设置处理时间，便于后台统计处理效率。

## 附件边界

反馈附件使用 `feedback` 业务前缀，通过 OSS 文件入口保存。当前设计是公开附件 URL，适合用户主动提交给管理端查看的文件。

如果后续要支持敏感附件，需要新增 PRIVATE 存储策略、鉴权下载接口和文档契约，不能只改前端展示。

## 修改注意事项

- 不要在反馈表中保存附件完整 URL，只保存 object key。
- 新增反馈类型或状态时需同步 API 文档和前端枚举。
- 调整附件可见性时需同步 `docs/internals/object_storage_module.md`。
