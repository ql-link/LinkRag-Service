# ToLink Service 前端 API 文档

> 文档版本：2.0
> 更新日期：2026-04-27
> 文档来源：基于当前 `link-api` Controller、Request/Response DTO、异常处理与配置代码梳理
> 适用范围：Web 前端、管理后台、联调文档、接口台账

---

## 一、文档说明

本文档以“当前代码真实实现”为准，适合前端直接联调。

本次校准重点：
- 认证请求头已按实际代码修正为 `satoken`
- 补全了数据集、知识文件、OSS、管理员知识文件配置接口
- 标注了哪些接口适合前端调用，哪些属于内部服务接口
- 标注了当前后端尚未提供但前端通常会需要的能力缺口

---

## 二、基础约定

### 2.1 基础地址

- 服务根地址：`http://{host}:8080`
- 业务接口前缀：`/api/v1`
- 完整示例：`http://{host}:8080/api/v1/auth/login`

### 2.2 数据格式

- 请求体：`application/json`
- 文件上传：`multipart/form-data`
- 字符编码：`UTF-8`
- 时间字段：后端返回 `LocalDateTime`，前端按 ISO 风格字符串处理

### 2.3 认证方式

除登录、注册、公共预览文件接口外，其余前端业务接口默认都需要登录。

当前项目实际使用的请求头是：

```http
satoken: {accessToken}
```

注意：
- 登录接口返回体里的 `tokenType` 虽然是 `Bearer`
- 但当前 Java 后端的 sa-token 配置实际校验的是 `satoken` 请求头
- 前端联调时不要使用 `Authorization: Bearer xxx` 代替

### 2.4 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| code | number | 业务状态码，成功固定为 `200` |
| message | string | 提示信息，成功通常为 `success` |
| data | any | 实际业务数据，可能为对象、数组、分页对象或 `null` |

### 2.5 分页响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "total": 100,
    "page": 1,
    "pageSize": 20,
    "totalPages": 5
  }
}
```

分页字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| items | array | 当前页数据 |
| total | number | 总记录数 |
| page | number | 当前页码，从 1 开始 |
| pageSize | number | 每页条数 |
| totalPages | number | 总页数 |

### 2.6 通用错误处理

全局异常由 `GlobalExceptionHandler` 统一处理，前端可按 `HTTP 状态码 + code + message` 三层处理。

常见错误响应示例：

```json
{
  "code": 400,
  "message": "username: 用户名不能为空",
  "data": null
}
```

```json
{
  "code": 401,
  "message": "未登录或登录已过期",
  "data": null
}
```

```json
{
  "code": 403,
  "message": "权限不足",
  "data": null
}
```

### 2.7 前端建议的统一拦截策略

- `401`：清理本地登录态并跳转登录页
- `403`：提示无权限或账号受限
- `400`：直接 toast `message`
- `409`：提示重复提交、重名或资源冲突
- `500` / `50001`：提示系统异常，请稍后重试

---

## 三、错误码清单

### 3.1 业务错误码

| 错误码 | 说明 | HTTP 状态 |
|--------|------|-----------|
| 10001 | 系统厂商不存在 | 404 |
| 10002 | 系统厂商已被禁用 | 400 |
| 10003 | 厂商被用户使用中，无法删除 | 409 |
| 10004 | 用户配置不存在 | 404 |
| 10005 | 用户配置已被禁用 | 400 |
| 10006 | 用户没有设置默认配置 | 404 |
| 10007 | API Key 格式无效 | 400 |
| 10008 | 模型不被该厂商支持 | 400 |
| 10009 | 用户已存在该厂商相同模型的配置 | 409 |
| 10010 | 知识文件上传配置不合法 | 400 |
| 20001 | 用户不存在 | 404 |
| 20002 | 密码错误 | 401 |
| 20003 | 账号已被禁用 | 403 |
| 20004 | 对话不存在 | 404 |
| 20005 | 无权访问该对话内容 | 403 |
| 40001 | OSS 上传业务错误 | 400 |
| 50001 | 系统内部错误 | 500 |

### 3.2 常见前端可见业务提示

这些提示不是枚举常量，但前端联调时会直接收到：

| 场景 | 可能提示 |
|------|----------|
| 登录失败 | 用户不存在 / 密码错误 / 账号已被禁用 |
| 数据集创建失败 | 当前用户下已存在同名数据集 |
| 文件上传失败 | 请选择要上传的文件 / 当前文件格式暂不支持 / 文件大小超过限制 / 文件名包含非法字符 |
| 文件重名 | 当前数据集下已存在同名原文件，请先重命名后再上传 |
| 解析任务失败 | 原文件未上传成功，不能创建解析任务 / 文件已上传，解析任务投递失败 |

---

## 四、数据模型总览

### 4.1 AuthResult

| 字段 | 类型 | 说明 |
|------|------|------|
| accessToken | string | 登录令牌 |
| tokenType | string | 固定为 `Bearer` |
| expiresIn | number | 过期时间，单位秒，当前默认 7 天即 `604800` |
| userId | number | 当前登录用户 ID |

### 4.2 UserProfileDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 用户 ID |
| username | string | 用户名 |
| nickname | string | 昵称 |
| email | string | 邮箱 |
| phone | string | 手机号 |
| avatarUrl | string | 头像 URL |
| role | string | `ADMIN` / `USER` |
| status | number | `1=启用`，`0=禁用` |

### 4.3 UserLLMConfigDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 配置 ID |
| configName | string | 配置名称 |
| providerType | string | 厂商类型，如 `openai` |
| providerName | string | 厂商名称 |
| modelName | string | 模型名称 |
| capabilities | string | 能力描述，当前为字符串 |
| apiKeyMasked | string | 脱敏后的 API Key |
| customApiBaseUrl | string | 自定义 API 地址 |
| priority | number | 优先级 |
| isActive | boolean | 是否启用 |
| isDefault | boolean | 是否默认 |
| timeoutMs | number | 超时毫秒数 |
| maxRetries | number | 最大重试次数 |
| streamEnabled | boolean | 是否启用流式 |
| extraConfig | string | 扩展配置，通常为 JSON 字符串 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

### 4.4 ConversationDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 对话 ID |
| title | string | 对话标题 |
| datasetId | number | 绑定的数据集 ID |
| lastConfigId | number | 上次使用配置 ID |
| lastModelName | string | 上次使用模型名称 |
| isPinned | boolean | 是否置顶 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

### 4.5 MessageDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 消息 ID |
| conversationId | number | 对话 ID |
| role | string | 消息角色，如 `user` / `assistant` |
| content | string | 消息内容 |
| configId | number | 配置 ID |
| modelName | string | 模型名称 |
| tokenCount | number | Token 数量 |
| createdAt | string | 创建时间 |

### 4.6 DatasetDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 数据集 ID |
| name | string | 数据集名称 |
| description | string | 数据集描述 |
| status | string | 当前实际状态值为 `ACTIVE` |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

### 4.7 KnowledgeFileDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 文件 ID |
| datasetId | number | 所属数据集 ID |
| originalFilename | string | 原始文件名 |
| fileSuffix | string | 文件后缀 |
| fileSize | number | 文件大小，单位字节 |
| uploadStatus | string | 上传状态 |
| isUploadSuccess | boolean | 上传是否成功 |
| parseNoticeStatus | string | 解析任务投递状态 |
| parseTaskId | string | 解析任务 ID |
| parseStatus | string | 解析执行状态 |
| isParseSuccess | boolean | 解析是否成功 |
| failureReason | string | 失败原因 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

### 4.8 KnowledgeFileConfigDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| maxSizeBytes | number | 单文件大小上限，单位字节 |
| allowedSuffixes | string[] | 允许上传的后缀白名单 |
| updatedBy | number | 最后修改人 ID |
| updatedAt | string | 最后修改时间 |

### 4.9 SystemProvider

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 厂商 ID |
| providerType | string | 厂商类型 |
| providerName | string | 厂商名称 |
| apiBaseUrl | string | API 基础地址 |
| supportedModels | string | 支持模型列表，当前为 JSON 字符串 |
| configSchema | string | 动态配置 Schema，当前为 JSON 字符串 |
| isActive | boolean | 是否启用 |
| priority | number | 优先级 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

### 4.10 用量统计 DTO

`UsageSummaryDTO`

| 字段 | 类型 | 说明 |
|------|------|------|
| totalCalls | number | 总调用次数 |
| totalTokens | number | 总 Token 数 |
| promptTokens | number | 提示词 Token 数 |
| completionTokens | number | 补全 Token 数 |
| averageLatencyMs | number | 平均延迟 |

`DailyUsageDTO`

| 字段 | 类型 | 说明 |
|------|------|------|
| date | string | 日期，格式 `yyyy-MM-dd` |
| calls | number | 调用次数 |
| promptTokens | number | 提示词 Token 数 |
| completionTokens | number | 补全 Token 数 |
| totalTokens | number | 总 Token 数 |

`UsageLogDTO`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 日志 ID |
| configId | number | 配置 ID |
| providerType | string | 厂商类型 |
| modelName | string | 模型名称 |
| promptTokens | number | 提示词 Token 数 |
| completionTokens | number | 补全 Token 数 |
| totalTokens | number | 总 Token 数 |
| latencyMs | number | 延迟毫秒数 |
| status | string | 调用状态 |
| errorMessage | string | 错误信息 |
| createdAt | string | 创建时间 |

---

## 五、前端接口清单总表

### 5.1 普通用户接口

| 模块 | 方法 | 路径 | 是否鉴权 | 说明 |
|------|------|------|----------|------|
| 认证 | POST | `/api/v1/auth/login` | 否 | 用户登录 |
| 认证 | POST | `/api/v1/auth/register` | 否 | 用户注册 |
| 认证 | POST | `/api/v1/auth/logout` | 是 | 退出登录 |
| 用户 | GET | `/api/v1/user/profile` | 是 | 获取当前用户信息 |
| 用户 | PATCH | `/api/v1/user/profile` | 是 | 修改个人资料 |
| LLM 配置 | GET | `/api/v1/llm/configs` | 是 | 获取配置列表 |
| LLM 配置 | POST | `/api/v1/llm/configs` | 是 | 创建配置 |
| LLM 配置 | PATCH | `/api/v1/llm/configs/{id}` | 是 | 更新配置 |
| LLM 配置 | DELETE | `/api/v1/llm/configs/{id}` | 是 | 删除配置 |
| 用量统计 | GET | `/api/v1/llm/usage/summary` | 是 | 用量汇总 |
| 用量统计 | GET | `/api/v1/llm/usage/daily` | 是 | 日趋势 |
| 用量统计 | GET | `/api/v1/llm/usage/logs` | 是 | 用量明细 |
| 对话 | POST | `/api/v1/chat/conversations` | 是 | 创建对话 |
| 对话 | GET | `/api/v1/chat/conversations` | 是 | 查询对话列表 |
| 对话 | GET | `/api/v1/chat/conversations/{id}/messages` | 是 | 查询消息历史 |
| 对话 | DELETE | `/api/v1/chat/conversations/{id}` | 是 | 删除对话 |
| 数据集 | POST | `/api/v1/datasets` | 是 | 创建数据集 |
| 数据集 | GET | `/api/v1/datasets` | 是 | 数据集分页列表 |
| 数据集 | GET | `/api/v1/datasets/{datasetId}` | 是 | 数据集详情 |
| 数据集 | DELETE | `/api/v1/datasets/{datasetId}` | 是 | 删除数据集 |
| 知识文件 | POST | `/api/v1/datasets/{datasetId}/knowledge-files` | 是 | 上传知识文件 |
| 知识文件 | GET | `/api/v1/datasets/{datasetId}/knowledge-files` | 是 | 知识文件列表 |
| 知识文件 | GET | `/api/v1/knowledge-files/{fileId}` | 是 | 知识文件详情 |
| 知识文件 | POST | `/api/v1/knowledge-files/{fileId}/parse-tasks` | 是 | 创建解析任务 |
| 知识文件 | DELETE | `/api/v1/knowledge-files/{fileId}` | 是 | 删除知识文件 |
| OSS | POST | `/api/v1/oss-files/{bizType}` | 否 | 通用业务文件上传 |
| OSS | GET | `/api/v1/oss-files/public/**` | 否 | 公共文件预览 |

### 5.2 管理员接口

| 模块 | 方法 | 路径 | 额外权限 | 说明 |
|------|------|------|----------|------|
| 用户管理 | GET | `/api/v1/admin/users` | `ADMIN` | 用户列表 |
| 用户管理 | PATCH | `/api/v1/admin/users/{id}/status` | `ADMIN` | 修改用户状态 |
| 用户管理 | PATCH | `/api/v1/admin/users/{id}/role` | `ADMIN` | 修改用户角色 |
| 厂商管理 | GET | `/api/v1/admin/providers` | `ADMIN` | 厂商列表 |
| 厂商管理 | POST | `/api/v1/admin/providers` | `ADMIN` | 创建厂商 |
| 厂商管理 | PATCH | `/api/v1/admin/providers/{id}` | `ADMIN` | 更新厂商 |
| 厂商管理 | DELETE | `/api/v1/admin/providers/{id}` | `ADMIN` | 删除厂商 |
| 厂商管理 | PATCH | `/api/v1/admin/providers/{id}/active` | `ADMIN` | 启用或禁用厂商 |
| 知识文件配置 | GET | `/api/v1/admin/knowledge-file-config` | `ADMIN` | 查看上传配置 |
| 知识文件配置 | PATCH | `/api/v1/admin/knowledge-file-config` | `ADMIN` | 修改上传配置 |

### 5.3 非前端直连接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/internal/knowledge-files/{fileId}/content` | 内部服务下载原始知识文件，依赖服务级 `Authorization`，前端不要直接调用 |

---

## 六、认证接口

### 6.1 用户登录

**接口**

```http
POST /api/v1/auth/login
```

**请求体**

```json
{
  "username": "admin",
  "password": "123456"
}
```

**参数说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "4f180049-c6e9-47e7-9a41-8571c9e5b87f",
    "tokenType": "Bearer",
    "expiresIn": 604800,
    "userId": 1
  }
}
```

**前端处理建议**

- 成功后保存 `accessToken`
- 后续请求统一追加请求头 `satoken`

### 6.2 用户注册

**接口**

```http
POST /api/v1/auth/register
```

**请求体**

```json
{
  "username": "user01",
  "password": "123456",
  "nickname": "用户01",
  "email": "user@example.com"
}
```

**参数说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 3-64 位 |
| password | string | 是 | 6-128 位 |
| nickname | string | 否 | 昵称 |
| email | string | 否 | 邮箱，需满足邮箱格式 |

**响应**

返回结构同登录接口，注册成功后后端会直接登录并返回 token。

### 6.3 退出登录

**接口**

```http
POST /api/v1/auth/logout
```

**请求头**

```http
satoken: {accessToken}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 七、用户接口

### 7.1 获取当前用户信息

**接口**

```http
GET /api/v1/user/profile
```

**请求头**

```http
satoken: {accessToken}
```

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "email": "admin@example.com",
    "phone": "13800138000",
    "avatarUrl": null,
    "role": "ADMIN",
    "status": 1
  }
}
```

### 7.2 修改个人资料

**接口**

```http
PATCH /api/v1/user/profile
```

**请求头**

```http
satoken: {accessToken}
Content-Type: application/json
```

**请求体**

```json
{
  "nickname": "新昵称",
  "email": "new@example.com",
  "phone": "13800138000",
  "avatarUrl": "https://cdn.example.com/avatar.png"
}
```

**说明**

- 所有字段均为可选
- `username`、`role`、`status` 不可通过该接口修改

---

## 八、LLM 配置接口

### 8.1 获取 LLM 配置列表

**接口**

```http
GET /api/v1/llm/configs
```

**请求头**

```http
satoken: {accessToken}
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| providerType | string | 否 | 厂商类型过滤 |
| isActive | boolean | 否 | 启用状态过滤 |

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "configName": "我的 OpenAI 配置",
      "providerType": "openai",
      "providerName": "OpenAI",
      "modelName": "gpt-4",
      "capabilities": null,
      "apiKeyMasked": "sk-****",
      "customApiBaseUrl": null,
      "priority": 50,
      "isActive": true,
      "isDefault": true,
      "timeoutMs": 60000,
      "maxRetries": 3,
      "streamEnabled": true,
      "extraConfig": null,
      "createdAt": "2026-04-27T10:00:00",
      "updatedAt": "2026-04-27T10:00:00"
    }
  ]
}
```

### 8.2 创建 LLM 配置

**接口**

```http
POST /api/v1/llm/configs
```

**请求体**

```json
{
  "providerType": "openai",
  "configName": "我的 OpenAI 配置",
  "apiKey": "sk-xxxxx",
  "modelName": "gpt-4",
  "priority": 50,
  "isDefault": false,
  "timeoutMs": 60000,
  "maxRetries": 3,
  "streamEnabled": true,
  "extraConfig": "{\"temperature\":0.7}"
}
```

**参数说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| providerType | string | 是 | 厂商类型 |
| configName | string | 是 | 配置名称 |
| apiKey | string | 是 | API Key，后端会加密存储 |
| modelName | string | 是 | 模型名称 |
| priority | number | 否 | 默认 `50` |
| isDefault | boolean | 否 | 默认 `false` |
| timeoutMs | number | 否 | 默认 `60000` |
| maxRetries | number | 否 | 默认 `3` |
| streamEnabled | boolean | 否 | 默认 `true` |
| extraConfig | string | 否 | JSON 字符串 |

### 8.3 更新 LLM 配置

**接口**

```http
PATCH /api/v1/llm/configs/{id}
```

**说明**

- 仅传要更新的字段
- 支持更新 `apiKey`
- 更新后的响应为 `Result<Void>`

**请求体示例**

```json
{
  "priority": 80,
  "isActive": true,
  "isDefault": true,
  "timeoutMs": 30000
}
```

### 8.4 删除 LLM 配置

**接口**

```http
DELETE /api/v1/llm/configs/{id}
```

---

## 九、用量统计接口

### 9.1 获取用量汇总

**接口**

```http
GET /api/v1/llm/usage/summary?startDate=2026-04-01&endDate=2026-04-27
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| startDate | string | 是 | 格式 `yyyy-MM-dd` |
| endDate | string | 是 | 格式 `yyyy-MM-dd` |

### 9.2 获取日度用量趋势

**接口**

```http
GET /api/v1/llm/usage/daily?startDate=2026-04-01&endDate=2026-04-27
```

### 9.3 获取用量明细

**接口**

```http
GET /api/v1/llm/usage/logs?startDate=2026-04-01&endDate=2026-04-27&page=1&pageSize=20
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| startDate | string | 是 | 开始日期 |
| endDate | string | 是 | 结束日期 |
| page | number | 否 | 默认 `1` |
| pageSize | number | 否 | 默认 `20` |

---

## 十、对话接口

### 10.1 创建对话

**接口**

```http
POST /api/v1/chat/conversations
```

**请求体**

```json
{
  "title": "我的新对话",
  "datasetId": 10001,
  "lastConfigId": 1
}
```

**参数说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 否 | 不传时后端默认 `新对话` |
| datasetId | number | 是 | 所属数据集 ID |
| lastConfigId | number | 否 | 初始配置 ID |

### 10.2 获取对话列表

**接口**

```http
GET /api/v1/chat/conversations?page=1&pageSize=20
```

**排序规则**

- 先按 `isPinned` 倒序
- 再按 `updatedAt` 倒序

### 10.3 获取对话消息历史

**接口**

```http
GET /api/v1/chat/conversations/{id}/messages?page=1&pageSize=50
```

**说明**

- 消息按 `createdAt` 正序返回
- 默认每页 `50`

### 10.4 删除对话

**接口**

```http
DELETE /api/v1/chat/conversations/{id}
```

**说明**

- 为逻辑删除
- 删除后对话不再出现在列表中

### 10.5 当前缺失的聊天能力

当前后端尚未对前端暴露以下接口：

- 发送消息接口
- 生成 AI 回复接口
- 流式回复接口
- 新增消息保存接口

说明：
- 代码里存在 `SaveMessageRequest` DTO
- 但当前 `Controller` 中没有对应公开 API
- 前端若要做完整聊天闭环，后端仍需补接口

---

## 十一、数据集接口

### 11.1 创建数据集

**接口**

```http
POST /api/v1/datasets
```

**请求体**

```json
{
  "name": "我的数据集",
  "description": "用于知识问答"
}
```

**参数说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 不能为空，最大 128 字符 |
| description | string | 否 | 最大 512 字符 |

### 11.2 获取数据集分页列表

**接口**

```http
GET /api/v1/datasets?page=1&pageSize=20
```

### 11.3 获取数据集详情

**接口**

```http
GET /api/v1/datasets/{datasetId}
```

### 11.4 删除数据集

**接口**

```http
DELETE /api/v1/datasets/{datasetId}
```

**说明**

删除数据集时，后端会尝试级联清理：

- 数据集下的知识原文件
- 数据集关联对话
- 对话关联消息

前端建议：

- 删除前弹出强提醒
- 提示“删除后无法恢复”

---

## 十二、知识文件接口

### 12.1 上传知识文件

**接口**

```http
POST /api/v1/datasets/{datasetId}/knowledge-files
```

**Content-Type**

```http
multipart/form-data
```

**表单字段**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | 是 | 上传文件 |
| parseImmediately | boolean | 否 | 是否立即创建解析任务，默认 `false` |

**curl 示例**

```bash
curl -X POST "http://{host}:8080/api/v1/datasets/10001/knowledge-files" \
  -H "satoken: {accessToken}" \
  -F "file=@/path/demo.pdf" \
  -F "parseImmediately=true"
```

**默认文件限制**

当前默认配置来自 `application.yml`：

| 配置项 | 默认值 |
|--------|--------|
| 最大大小 | `20971520` 字节，即 `20MB` |
| 允许后缀 | `md`, `markdown`, `pdf`, `docx`, `txt` |

### 12.2 获取知识文件列表

**接口**

```http
GET /api/v1/datasets/{datasetId}/knowledge-files?page=1&pageSize=20
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| uploadStatus | string | 否 | 上传状态过滤 |
| parseNoticeStatus | string | 否 | 解析任务投递状态过滤 |
| parseStatus | string | 否 | 解析状态过滤 |
| page | number | 否 | 默认 `1` |
| pageSize | number | 否 | 默认 `20` |

**支持的状态值**

`uploadStatus`

| 值 | 说明 |
|----|------|
| `UPLOADING` | 上传中 |
| `UPLOAD_SUCCESS` | 上传成功 |
| `UPLOAD_FAILED` | 上传失败 |

`parseNoticeStatus`

| 值 | 说明 |
|----|------|
| `PARSE_NOTICE_PENDING` | 待投递 |
| `PARSE_NOTICE_SENT` | 已投递 |
| `PARSE_NOTICE_FAILED` | 投递失败 |

`parseStatus`

| 值 | 说明 |
|----|------|
| `NOT_STARTED` | 未开始 |
| `PENDING` | 已排队 |
| `PROCESSING` | 解析中 |
| `SUCCESS` | 成功 |
| `FAILED` | 失败 |

### 12.3 获取知识文件详情

**接口**

```http
GET /api/v1/knowledge-files/{fileId}
```

### 12.4 创建解析任务

**接口**

```http
POST /api/v1/knowledge-files/{fileId}/parse-tasks
```

**说明**

- 文件尚未上传成功时不能创建解析任务
- 如果当前文件已经是 `PENDING` 状态，后端会直接返回当前记录

### 12.5 删除知识文件

**接口**

```http
DELETE /api/v1/knowledge-files/{fileId}
```

**说明**

- 后端会先尝试删除 OSS 私有文件
- 再删除数据库记录
- 若对象删除成功但数据库删除失败，后端会返回错误

### 12.6 前端展示建议

- 文件列表页至少展示：
  - `originalFilename`
  - `fileSuffix`
  - `fileSize`
  - `uploadStatus`
  - `parseNoticeStatus`
  - `parseStatus`
  - `failureReason`
  - `createdAt`
- 状态建议拆为三列，不建议合并成一个总状态
- 这样更容易定位“上传成功但解析任务投递失败”的问题

---

## 十三、OSS 文件接口

### 13.1 通用业务文件上传

**接口**

```http
POST /api/v1/oss-files/{bizType}
```

**说明**

- 当前代码层面没有 `@SaCheckLogin`
- 即默认不要求登录
- 适合头像上传、富文本图片上传、临时素材上传等公共能力

**请求格式**

`multipart/form-data`

**表单字段**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | 是 | 上传文件 |
| bizType | path | 是 | 业务类型 |

**响应示例**

```json
{
  "code": 200,
  "message": "success",
  "data": "/api/v1/oss-files/public/avatar/2026/04/demo.png"
}
```

注意：
- 返回值 `data` 是可预览 URL
- 不是 object key

### 13.2 公共文件预览

**接口**

```http
GET /api/v1/oss-files/public/**
```

**说明**

- 返回文件流
- 可直接作为图片地址、附件预览地址使用

---

## 十四、管理员接口

所有管理员接口都需要同时满足：

- 请求头带 `satoken`
- 当前用户角色为 `ADMIN`

### 14.1 获取用户列表

**接口**

```http
GET /api/v1/admin/users?page=1&size=10
```

**返回**

`PageResult<UserProfileDTO>`

### 14.2 修改用户状态

**接口**

```http
PATCH /api/v1/admin/users/{id}/status
```

**请求体**

```json
{
  "status": 1
}
```

**状态定义**

| 值 | 说明 |
|----|------|
| 1 | 启用 |
| 0 | 禁用 |

### 14.3 修改用户角色

**接口**

```http
PATCH /api/v1/admin/users/{id}/role
```

**请求体**

```json
{
  "role": "ADMIN"
}
```

**角色值**

| 值 | 说明 |
|----|------|
| `ADMIN` | 管理员 |
| `USER` | 普通用户 |

### 14.4 获取厂商列表

**接口**

```http
GET /api/v1/admin/providers?page=1&size=10
```

### 14.5 创建厂商

**接口**

```http
POST /api/v1/admin/providers
```

**请求体**

```json
{
  "providerType": "openai",
  "providerName": "OpenAI",
  "apiBaseUrl": "https://api.openai.com/v1",
  "supportedModels": "[\"gpt-4\",\"gpt-4o\"]",
  "configSchema": "{\"type\":\"object\"}",
  "isActive": true,
  "priority": 10
}
```

### 14.6 更新厂商

**接口**

```http
PATCH /api/v1/admin/providers/{id}
```

**请求体**

```json
{
  "providerName": "OpenAI",
  "priority": 20,
  "isActive": true
}
```

### 14.7 删除厂商

**接口**

```http
DELETE /api/v1/admin/providers/{id}
```

### 14.8 启用或禁用厂商

**接口**

```http
PATCH /api/v1/admin/providers/{id}/active?isActive=false
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| isActive | boolean | 是 | 是否启用 |

### 14.9 获取知识文件上传配置

**接口**

```http
GET /api/v1/admin/knowledge-file-config
```

### 14.10 修改知识文件上传配置

**接口**

```http
PATCH /api/v1/admin/knowledge-file-config
```

**请求体**

```json
{
  "maxSizeBytes": 20971520,
  "allowedSuffixes": ["md", "pdf", "txt"]
}
```

**参数说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| maxSizeBytes | number | 是 | 必须大于 0 |
| allowedSuffixes | string[] | 是 | 至少一个后缀 |

---

## 十五、内部接口说明

### 15.1 内部知识文件下载接口

**接口**

```http
GET /api/v1/internal/knowledge-files/{fileId}/content?taskId={taskId}
```

**认证方式**

```http
Authorization: Bearer {serviceToken}
```

**用途**

- 供内部解析服务下载原始知识文件
- 不是给浏览器前端使用的接口

---

## 十六、前端页面到接口映射建议

### 16.1 登录注册页

- 登录：`POST /api/v1/auth/login`
- 注册：`POST /api/v1/auth/register`
- 退出：`POST /api/v1/auth/logout`

### 16.2 用户中心

- 获取资料：`GET /api/v1/user/profile`
- 编辑资料：`PATCH /api/v1/user/profile`

### 16.3 LLM 配置页

- 列表：`GET /api/v1/llm/configs`
- 新建：`POST /api/v1/llm/configs`
- 编辑：`PATCH /api/v1/llm/configs/{id}`
- 删除：`DELETE /api/v1/llm/configs/{id}`

### 16.4 用量统计页

- 汇总卡片：`GET /api/v1/llm/usage/summary`
- 趋势图：`GET /api/v1/llm/usage/daily`
- 明细表：`GET /api/v1/llm/usage/logs`

### 16.5 数据集页

- 创建数据集：`POST /api/v1/datasets`
- 数据集列表：`GET /api/v1/datasets`
- 数据集详情：`GET /api/v1/datasets/{datasetId}`
- 删除数据集：`DELETE /api/v1/datasets/{datasetId}`

### 16.6 知识文件页

- 上传文件：`POST /api/v1/datasets/{datasetId}/knowledge-files`
- 文件列表：`GET /api/v1/datasets/{datasetId}/knowledge-files`
- 文件详情：`GET /api/v1/knowledge-files/{fileId}`
- 重新发起解析：`POST /api/v1/knowledge-files/{fileId}/parse-tasks`
- 删除文件：`DELETE /api/v1/knowledge-files/{fileId}`

### 16.7 对话页

- 创建对话：`POST /api/v1/chat/conversations`
- 对话列表：`GET /api/v1/chat/conversations`
- 历史消息：`GET /api/v1/chat/conversations/{id}/messages`
- 删除对话：`DELETE /api/v1/chat/conversations/{id}`

说明：
- 目前还不能直接完成“发消息并生成 AI 回复”的完整闭环
- 因为后端未提供公开发送消息接口

### 16.8 管理后台

- 用户管理：`/api/v1/admin/users` 及其 PATCH 子接口
- 厂商管理：`/api/v1/admin/providers` 系列
- 文件上传策略配置：`/api/v1/admin/knowledge-file-config`

---

## 十七、联调注意事项

### 17.1 Token 头一定要用 `satoken`

错误写法：

```http
Authorization: Bearer {token}
```

当前项目正确写法：

```http
satoken: {token}
```

### 17.2 文件上传要区分两套接口

- 知识库文件上传：`/api/v1/datasets/{datasetId}/knowledge-files`
  - 需要登录
  - 有数据集归属
  - 有解析任务状态
- 通用 OSS 上传：`/api/v1/oss-files/{bizType}`
  - 当前默认不校验登录
  - 返回公共预览 URL

### 17.3 查询列表页码从 1 开始

当前所有分页接口都以 `1` 作为第一页。

### 17.4 日期格式固定为 `yyyy-MM-dd`

用量统计接口中的 `startDate` 和 `endDate` 都要求该格式。

### 17.5 状态值建议直接按后端返回值建前端枚举

尤其是知识文件相关状态，不建议前端自行二次发明枚举名。

---

## 十八、当前接口缺口

以下能力是前端常见需求，但当前代码中还没有对外开放的接口：

### 18.1 聊天发送消息接口缺失

当前没有类似：

```http
POST /api/v1/chat/messages
POST /api/v1/chat/conversations/{id}/messages
```

因此前端目前只能：

- 创建对话
- 查对话列表
- 查历史消息
- 删除对话

不能直接：

- 发送用户消息
- 触发模型回答
- 获取流式回答

### 18.2 对话编辑能力缺失

当前没有：

- 修改对话标题
- 对话置顶/取消置顶
- 清空对话消息

### 18.3 数据集编辑能力缺失

当前没有：

- 修改数据集名称
- 修改数据集描述

---

## 十九、推荐的前端类型定义

```ts
export interface Result<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface AuthResult {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  userId: number;
}

export interface UserProfileDTO {
  id: number;
  username: string;
  nickname: string | null;
  email: string | null;
  phone: string | null;
  avatarUrl: string | null;
  role: "ADMIN" | "USER";
  status: 0 | 1;
}

export type KnowledgeUploadStatus =
  | "UPLOADING"
  | "UPLOAD_SUCCESS"
  | "UPLOAD_FAILED";

export type KnowledgeParseNoticeStatus =
  | "PARSE_NOTICE_PENDING"
  | "PARSE_NOTICE_SENT"
  | "PARSE_NOTICE_FAILED";

export type KnowledgeParseStatus =
  | "NOT_STARTED"
  | "PENDING"
  | "PROCESSING"
  | "SUCCESS"
  | "FAILED";
```

---

## 二十、结论

截至 2026-04-27，当前项目已经具备以下前端可直接接入的能力：

- 认证登录注册
- 用户资料管理
- LLM 配置管理
- 用量统计
- 数据集管理
- 知识文件上传与解析任务管理
- 对话列表与历史消息查询
- OSS 通用上传
- 管理员用户/厂商/知识文件配置管理

当前最主要的接口缺口是：

- 缺少真正的“聊天发送消息 / AI 回复”公开接口

如果前端要做完整聊天产品页，建议后端下一步优先补齐该链路接口。
