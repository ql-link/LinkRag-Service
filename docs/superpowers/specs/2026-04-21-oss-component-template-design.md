# OSS 组件模板化重构设计方案

## 一、背景

当前 `toLink-components-oss` 模块同时承担了两类职责：

1. 基础设施职责：对象存储抽象、provider 实现、配置模型、私有文件解析。
2. 业务职责：上传接口、业务类型规则、公开预览接口约定。

这导致 OSS 组件边界偏重，不符合当前项目里 MQ 组件的设计风格。  
本次改造的目标，是把 OSS 组件收敛为“内部基础设施模板模块”，只保留抽象能力和存储实现；具体业务上传规则、接口定义、业务编排全部下沉到 `link-service` 和 `link-api`。

## 二、改造目标

- 让 `toLink-components-oss` 只保留基础设施能力。
- 将业务上传规则、业务接口控制器迁出 OSS 组件。
- 保持现有知识文件上传能力不回归。
- 在 `link-service` 中建立项目级 OSS 应用服务层。
- 通过 TDD 方式逐步迁移，确保每一步有测试兜底。

## 三、非目标

- 不重设计知识文件上传本身的业务语义。
- 不在本次改造中额外扩展新的 OSS 厂商。
- 不在本次改造中主动变更对外接口协议，除非迁移本身必须调整。

## 四、目标职责边界

### 4.1 `toLink-components-oss`

改造后只保留基础设施相关内容：

- `OssProperties`
- `OssSavePlaceEnum`
- `OssServiceTypeEnum`
- `IOssService`
- `LocalFileService`
- `MinioFileService`
- `PrivateFileResolver`
- OSS 自动装配能力

改造后必须移出的内容：

- `OssFileController`
- `LocalOssPreviewController`
- `OssFileConfig`
- 所有内置 `bizType` 规则定义
- 所有与接口路径、业务白名单、业务大小限制耦合的代码

OSS 组件改造后只回答以下问题：

- 文件如何上传
- 文件如何下载
- 私有文件如何解析为本地文件

OSS 组件改造后不再回答以下问题：

- 系统里有哪些业务上传类型
- 每个业务允许哪些后缀
- 每个业务的文件大小限制是多少
- 每个业务的接口路径是什么
- 每个业务的 object key 目录结构如何组织

### 4.2 `link-service`

`link-service` 负责承接项目级的 OSS 应用层能力，用来连接业务规则与底层 `IOssService`。

建议新增的结构如下：

- `service/oss/OssUploadRule`
- `service/oss/OssUploadRuleRegistry`
- `service/oss/OssObjectKeyGenerator`
- `service/oss/OssApplicationService`

职责包括：

- 维护项目内的业务上传规则
- 做文件后缀与大小校验
- 生成 object key
- 映射业务上传场景到 `PUBLIC` / `PRIVATE`
- 调用 `IOssService` 完成上传

知识文件上传仍然保留自己的业务流程，但底层通用上传编排必须通过新的 service 层结构完成，而不能再依赖 OSS 组件内部的业务类。

### 4.3 `link-api`

`link-api` 负责所有对外暴露的上传与预览接口。

职责包括：

- 上传控制器
- 公开文件预览控制器
- 请求与响应模型绑定
- API 层参数校验

改造完成后，所有接口路径与外部协议都应由 `link-api` 维护，而不再由 OSS 组件内部提供。

## 五、迁移方案

### 阶段一：先锁住 OSS 组件基础能力

这一阶段先不做大规模迁移，先保证 OSS 组件在瘦身后仍然能稳定工作。

需要保留并补强的测试包括：

- `LocalFileServiceTest`
- `MinioFileServiceTest`
- `PrivateFileResolver` 相关测试

本阶段目标：

- 组件移除业务代码后，基础上传/下载能力不受影响
- provider 行为先被测试锁住，再进行后续迁移

### 阶段二：在 `link-service` 中建立 OSS 应用服务层

这一阶段新增服务层抽象，并通过测试把能力“推出来”。

核心测试场景：

- 已注册业务类型可以上传成功
- 不支持的文件后缀会被拒绝
- 超出大小限制的文件会被拒绝
- 公开文件上传后返回预览 URL
- 私有文件上传后返回 object key
- object key 生成符合项目约定
- 未知业务类型会被拒绝

本阶段完成后，业务上传规则不再依赖组件里的 `OssFileConfig`。

### 阶段三：将上传和预览控制器迁移到 `link-api`

这一阶段把接口层职责彻底从 OSS 组件迁出。

核心测试场景：

- 上传接口仍然可以处理 multipart 请求
- 本地公开文件预览接口仍然可以正常访问
- 非法业务类型仍然返回明确错误
- 非法文件大小、非法文件后缀仍然返回明确错误

本阶段完成后，可正式删除组件中的：

- `OssFileController`
- `LocalOssPreviewController`

### 阶段四：知识文件上传回归验证

这一阶段重点保证知识文件上传业务不被架构重构影响。

核心验证场景：

- 文件记录仍然正常落库
- OSS 上传仍然成功
- 私有 object key 仍然正确保存
- 内部下载仍然可用
- 解析任务相关流程不回归

## 六、TDD 执行顺序

本次改造必须严格遵循 `Red -> Green -> Refactor`。

### 第 1 轮

先为 OSS 组件基础边界写失败测试。

目标：

- 先保护组件的基础设施职责
- 确保组件的测试只覆盖“基础设施能力”，不再覆盖业务接口

### 第 2 轮

为 `link-service` 中新增的 OSS 应用服务层写失败测试。

目标：

- 用测试驱动出新的上传规则模型
- 用测试驱动出新的 object key 生成与上传编排能力

### 第 3 轮

为迁移后的 `link-api` 控制器写失败测试。

目标：

- 用测试驱动控制器迁移
- 保证对外接口行为稳定

### 第 4 轮

为知识文件上传链路写失败回归测试。

目标：

- 架构调整后，知识文件上传行为保持不变
- 数据库、OSS、内部下载、解析任务链路都可回归验证

## 七、类级别迁移清单

### 7.1 迁出 OSS 组件

- `link-components/toLink-components-oss/.../controller/OssFileController.java`
- `link-components/toLink-components-oss/.../controller/LocalOssPreviewController.java`
- `link-components/toLink-components-oss/.../model/OssFileConfig.java`

### 7.2 保留在 OSS 组件

- `link-components/toLink-components-oss/.../config/OssProperties.java`
- `link-components/toLink-components-oss/.../config/OssAutoConfiguration.java`
- `link-components/toLink-components-oss/.../service/IOssService.java`
- `link-components/toLink-components-oss/.../service/LocalFileService.java`
- `link-components/toLink-components-oss/.../service/MinioFileService.java`
- `link-components/toLink-components-oss/.../service/PrivateFileResolver.java`
- 相关枚举类

### 7.3 新增到 `link-service`

- 项目级 OSS 上传规则模型
- 项目级 OSS 上传规则注册表
- 项目级 OSS object key 生成器
- 项目级 OSS 应用服务

### 7.4 新增到 `link-api`

- 迁移后的上传控制器
- 迁移后的公开预览控制器

## 八、兼容性要求

- 本次迁移期间，现有对外接口协议应保持稳定；如果后续需要改接口，应该单独发起变更。
- 知识文件上传在迁移完成后，必须通过新的 service 层结构间接调用 `IOssService`。
- 本地公开文件预览能力在控制器迁移后仍必须存在。

## 九、风险与应对

### 风险 1：组件控制器与 provider 行为存在隐藏耦合

应对方式：

- 先补 provider 与 resolver 测试，再做迁移

### 风险 2：控制器迁移时破坏现有上传接口

应对方式：

- 先写 controller 测试，再迁移接口实现

### 风险 3：知识文件上传链路回归

应对方式：

- 保留知识文件专属回归测试，覆盖落库、OSS 上传、内部下载、解析任务

## 十、成功标准

- `toLink-components-oss` 仅保留基础设施抽象与 provider 实现
- 所有业务上传规则都迁出 OSS 组件
- 所有上传与预览控制器都归属 `link-api`
- 知识文件上传链路保持可用
- 组件层、服务层、接口层均有对应测试覆盖

