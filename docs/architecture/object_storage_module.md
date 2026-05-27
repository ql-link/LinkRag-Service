# Object Storage Module

## 职责

OSS 组件位于 `link-components/toLink-components-oss`，业务上传入口位于 `OssApplicationService` 和知识文件服务。

## 当前能力

- `IOssService`：统一对象存储接口。
- `LocalFileService`：本地文件实现。
- `MinioFileService`：MinIO 实现。
- `PrivateFileResolver`：私有对象本地解析与缓存。
- `OssObjectKeyGenerator`、`OssUploadRuleRegistry`：业务对象 key 与上传规则。

## 约定

新增上传业务需明确 public/private 边界、对象 key 规则、访问 URL 和删除策略。
