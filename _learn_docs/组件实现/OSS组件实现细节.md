# OSS 组件实现细节

本文档描述当前 ToLink OSS 体系的职责边界与实现方式。

本次重构后的核心原则只有一条：

`toLink-components-oss` 只提供存储模板能力，不承载业务上传规则、不暴露业务接口。

业务编排放在 `link-service`，对外 HTTP 接口放在 `link-api`，整体结构与 MQ 组件保持一致。

## 1. 设计目标

当前 OSS 体系需要解决三个问题：

- 屏蔽具体存储实现差异，业务层不直接依赖 MinIO、本地文件系统等 SDK 细节。
- 统一公开文件与私有文件的存储语义。
- 把“怎么存”和“为什么这样存”分层，避免组件层夹带业务规则。

重构后的分层目标如下：

- `toLink-components-oss`：提供底层存储能力与统一抽象。
- `link-service`：定义业务上传规则、对象 key 生成策略、上传编排流程。
- `link-api`：暴露上传接口与本地公开文件预览接口。

## 2. 模块职责边界

### 2.1 OSS 组件层

模块位置：

```text
link-components/toLink-components-oss
```

组件层保留以下内容：

- `OssProperties`
- `OssSavePlaceEnum`
- `OssServiceTypeEnum`
- `IOssService`
- `LocalFileService`
- `MinioFileService`
- `PrivateFileResolver`
- `OssAutoConfiguration`

组件层职责：

- 读取 OSS 基础配置。
- 根据 `service-type` 装配具体 provider。
- 提供统一上传、下载、私有文件解析能力。
- 管理本地文件系统或云存储 SDK 的接入细节。

组件层不再负责：

- 业务 `bizType` 管理。
- 文件后缀校验。
- 文件大小限制。
- 对外上传接口。
- 公开文件预览接口。

### 2.2 Service 业务编排层

模块位置：

```text
link-service
```

当前新增的 OSS 业务编排类：

- `OssApplicationService`
- `OssApplicationServiceImpl`
- `OssUploadRule`
- `OssUploadRuleRegistry`
- `OssObjectKeyGenerator`

这一层负责：

- 根据 `bizType` 选择上传规则。
- 校验文件是否为空。
- 校验文件后缀是否合法。
- 校验文件大小是否超限。
- 决定文件保存到 `PUBLIC` 还是 `PRIVATE`。
- 生成最终 object key。
- 调用 `IOssService` 完成真正存储。

这里的职责不是直接操作磁盘或 MinIO，而是定义业务层的上传编排规则。

### 2.3 API 接口层

模块位置：

```text
link-api
```

当前 OSS 相关接口类：

- `OssFileController`
- `LocalOssPreviewController`

这一层负责：

- 提供 `/api/v1/oss-files/{bizType}` 上传入口。
- 提供 `/api/v1/oss-files/public/**` 本地公开文件预览入口。
- 调用 `link-service` 的业务编排服务。

API 层不直接调用具体 provider，也不直接维护上传规则。

## 3. 核心抽象

### 3.1 IOssService

文件：

```text
link-components/toLink-components-oss/src/main/java/com/qingluo/link/components/oss/service/IOssService.java
```

它是 OSS 组件对上层暴露的统一存储接口。

当前核心语义：

- `upload2PreviewUrl(PUBLIC, file, objectKey)`：上传公开文件，返回可访问地址。
- `upload2PreviewUrl(PRIVATE, file, objectKey)`：上传私有文件，返回 object key。
- `downloadFile(...)`：下载或复制远端对象到本地文件。

注意：

- 这里的方法名沿用了 `upload2PreviewUrl`，但对私有文件来说，返回值不是预览 URL，而是业务侧保存用的 object key。

### 3.2 OssSavePlaceEnum

组件统一定义两类存储位置：

- `PUBLIC`
- `PRIVATE`

业务层只决定“放哪里”，组件层负责“怎么放”。

### 3.3 OssServiceTypeEnum

组件通过配置切换 provider，例如：

- `local`
- `minio`
- `s3`
- `aliyun-oss`

业务层不感知当前具体使用哪一种 provider。

## 4. 配置模型

文件：

```text
link-components/toLink-components-oss/src/main/java/com/qingluo/link/components/oss/config/OssProperties.java
```

配置前缀：

```yaml
tolink:
  oss:
```

当前主要配置包括：

- `service-type`
- `file-root-path`
- `file-public-path`
- `file-private-path`
- `public-base-url`
- `minio.endpoint`
- `minio.public-bucket-name`
- `minio.private-bucket-name`
- `minio.access-key`
- `minio.secret-key`

其中：

- `public-base-url` 仅用于公开文件 URL 拼装。
- 若启用本地公开文件上传，但未配置 `public-base-url`，当前会直接报错，不再使用隐式默认 API 路径兜底。

项目启动时主要使用：

```text
link-api/src/main/resources/application-local.yml
```

## 5. 本地与 MinIO provider 行为

### 5.1 LocalFileService

职责：

- 把公开文件写入 `file-public-path`。
- 把私有文件写入 `file-private-path`。
- 返回公开访问 URL 或私有 object key。
- 处理本地路径归一化，避免目录穿越。

公开文件返回：

```text
{public-base-url}/{objectKey}
```

私有文件返回：

```text
{objectKey}
```

### 5.2 MinioFileService

职责：

- 根据 `PUBLIC` / `PRIVATE` 选择对应 bucket。
- 上传对象到 MinIO。
- 对公开对象返回可访问 URL。
- 对私有对象返回 object key。

MinIO 只负责存储行为，不负责业务校验。

## 6. 业务上传编排

业务上传入口现在经过 `OssApplicationServiceImpl`。

处理流程如下：

1. 接收 `bizType` 和 `MultipartFile`。
2. 从 `OssUploadRuleRegistry` 查找对应业务规则。
3. 校验文件是否为空。
4. 校验文件后缀。
5. 校验文件大小。
6. 通过 `OssObjectKeyGenerator` 生成 object key。
7. 调用 `IOssService.upload2PreviewUrl(...)`。
8. 返回上传结果。

这一步的含义可以概括为：

- 定义“存哪里”：`PUBLIC` 还是 `PRIVATE`。
- 定义“怎么命名”：object key 如何生成。
- 定义“什么文件允许上传”：后缀、大小、业务类型规则。

但它不负责真正执行 MinIO 或本地写入，那部分属于 OSS 组件 provider。

## 7. 当前上传规则

当前规则注册在：

```text
link-service/src/main/java/com/qingluo/link/service/oss/OssUploadRuleRegistry.java
```

已注册业务类型：

| bizType | 存储位置 | 允许后缀 | 最大大小 |
| --- | --- | --- | --- |
| `avatar` | `PUBLIC` | `jpg` `jpeg` `png` `gif` `webp` | 5 MB |
| `chatImage` | `PUBLIC` | `jpg` `jpeg` `png` `gif` `webp` | 5 MB |
| `document` | `PRIVATE` | `pdf` `doc` `docx` `txt` `md` | 20 MB |
| `cert` | `PRIVATE` | `*` | 5 MB |

后续新增上传类型时，应优先扩展 `link-service` 规则注册，而不是修改 OSS 组件。

## 8. 对外接口

### 8.1 文件上传

接口：

```text
POST /api/v1/oss-files/{bizType}
```

控制器：

```text
link-api/src/main/java/com/qingluo/link/api/controller/OssFileController.java
```

职责：

- 接收上传文件。
- 将请求转发给 `OssApplicationService`。
- 返回统一响应结果。

### 8.2 本地公开文件预览

接口：

```text
GET /api/v1/oss-files/public/**
```

控制器：

```text
link-api/src/main/java/com/qingluo/link/api/controller/LocalOssPreviewController.java
```

职责：

- 仅处理本地公开文件的预览读取。
- 按 object key 解析本地文件路径。
- 返回文件流与内容类型。

这部分放在 `link-api`，是因为它本质上属于对外 HTTP 能力，而不是存储模板能力。

## 9. 与旧方案的差异

旧方案的问题：

- OSS 组件内部同时包含 controller、业务上传配置、provider 实现。
- 组件层混入业务规则，边界不清晰。
- 本地公开文件访问路径对 API 路由存在隐式耦合。

新方案的变化：

- 组件层只保留基础存储抽象和 provider。
- 上传规则迁移到 `link-service`。
- 上传与预览接口迁移到 `link-api`。
- `public-base-url` 变成显式配置，不再在组件内部写死 API 路径。

## 10. 测试覆盖

当前已补充的关键测试包括：

- `OssComponentBoundaryTest`
  - 验证 OSS 组件不再暴露业务 controller 和业务规则类。
- `LocalFileServiceTest`
  - 验证本地公开/私有上传行为。
  - 验证未配置 `public-base-url` 时公开上传直接失败。
- `MinioFileServiceTest`
  - 验证 MinIO provider 的核心行为。
- `OssObjectKeyGeneratorTest`
  - 验证 object key 生成规则。
- `OssApplicationServiceImplTest`
  - 验证业务编排层的校验与上传委托。
- `OssFileControllerTest`
  - 验证上传接口和本地公开预览接口。

## 11. 后续扩展建议

如果未来新增阿里云 OSS、腾讯 COS、AWS S3，建议继续保持当前边界：

- 在 `toLink-components-oss` 中新增 provider 实现。
- 在 `OssProperties` 中补充对应配置。
- 不在组件里新增 controller。
- 不在组件里新增业务 `bizType` 校验逻辑。

如果未来某个业务模块需要更细粒度上传策略，也应优先在 `link-service` 中扩展应用服务或规则注册，而不是回退到组件层处理。
