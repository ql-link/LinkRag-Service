# OSS 组件说明

## 1. 组件定位

OSS 组件是项目的文件存储 framework，负责提供：

- 统一文件存储接口 `IOssService`
- 本地文件实现
- MinIO 实现
- 私有文件解析辅助 `PrivateFileResolver`
- 统一配置 `OssProperties`

它解决的是“如何接入对象存储与访问文件”，不是“某个具体业务文件记录怎么建模”。

## 2. 代码归属

- 模块：`link-components/toLink-components-oss`
- 主要包：`com.qingluo.link.components.oss`

## 3. 核心代码结构

| 代码 | 作用 |
| --- | --- |
| `IOssService` | 统一的 OSS 抽象接口 |
| `LocalFileService` | 本地文件实现 |
| `MinioFileService` | MinIO 实现 |
| `PrivateFileResolver` | 私有 object key 到本地文件的解析器 |
| `OssAutoConfiguration` | 组件自动装配入口 |
| `OssProperties` | 配置承载类 |

## 4. 这个组件能做什么

### 4.1 `IOssService`

统一提供：

- 上传文件
- 下载文件
- 删除文件
- 获取 bucket 名称

接口语义：

- 公有文件上传：返回可直接访问的 URL
- 私有文件上传：返回相对 `objectKey`

### 4.2 `PrivateFileResolver`

提供：

- 把私有 `objectKey` 解析成可访问的本地 `File`
- 若本地没有，按需从远端拉取
- 删除本地缓存和 `.notexists` 标记

### 4.3 provider 切换

当前支持：

- `local`
- `minio`

后续如果要扩展新的 OSS 厂商，也应该沿着 `IOssService` 这套抽象接。

## 5. AI 什么时候应该用这个组件

当你要做的事情符合下面任一情况时，应优先考虑这个组件：

- 上传文件
- 下载文件
- 删除对象
- 设计 object key
- 处理公有/私有文件访问
- 新增底层对象存储实现

如果需求是：

- 新增文件记录表
- 新增文件状态字段
- 设计文件解析状态流转

那不能只看 OSS 组件，还要回到 MySQL 设计。

## 6. AI 接入时怎么操作

### 6.1 只是使用现有 OSS 能力

通常写法：

1. 在业务 Service 中注入 `IOssService`
2. 生成 `objectKey`
3. 调用上传/下载/删除接口
4. 把返回结果写入业务表

### 6.2 需要访问私有文件

通常写法：

1. 在业务 Service 中注入 `PrivateFileResolver`
2. 传入私有 `objectKey`
3. 获取本地 `File`
4. 交给下游逻辑处理

### 6.3 想新增一个 OSS provider

推荐做法：

1. 新实现一个 `IOssService`
2. 用配置条件控制启用
3. 复用 `OssSavePlaceEnum`、`OssProperties`
4. 保持业务层不感知底层 provider 差异

建议放置位置：

- framework 代码：`link-components/toLink-components-oss/`
- 业务接入代码：`link-service/`

## 7. AI 需要写哪些代码

### 场景一：业务接入 OSS

通常需要写：

- 业务 service 中的上传/下载/删除调用
- object key 生成逻辑
- 业务表元数据写入逻辑

通常不需要改：

- `IOssService`
- `OssAutoConfiguration`
- provider 实现

### 场景二：扩展 OSS framework

只有在下面情况才应该改 framework 模块：

- 要新增一种 OSS provider
- 现有接口抽象不够
- 现有私有文件解析机制不够

这类代码应放在：

- `link-components/toLink-components-oss/src/main/java/com/qingluo/link/components/oss/`

## 8. 关键配置

- 配置前缀：`tolink.oss`
- 当前配置承载类：`OssProperties`
- 关键配置包括：
  - `service-type`
  - `file-root-path`
  - `file-public-path`
  - `file-private-path`
  - `public-base-url`
  - `minio.*`
  - `aliyun-oss.*`

## 9. 不该怎么用

- 不要把业务状态流转写进 OSS framework
- 不要在业务层直接依赖某个具体 provider
- 不要只存 URL 不存 `objectKey`
- 不要把 object key 规则散落在多个地方

## 10. 读取顺序建议

当 AI 要使用 OSS 组件时，推荐顺序：

1. 先读本文件
2. 再读 `docs/组件和数据库约定/middleware_contract.md` 中的 OSS 约定
3. 再看现有业务 service 怎么生成 object key、怎么写元数据
4. 最后在 `technical_design.md` 中写本次业务接法
