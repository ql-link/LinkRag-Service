---
name: api-design-standards
description: Design, implement, or review REST API endpoints in this project. Use when creating controllers, DTOs, request/response models, OpenAPI/Swagger docs, error responses, pagination APIs, authentication headers, or when checking whether an API follows ToLink REST conventions.
---

# API Design Standards

## Purpose

Use this skill to design, implement, or review ToLink HTTP APIs. It turns API work into a consistent REST-style contract across URL naming, HTTP methods, status codes, request parameters, response envelopes, error codes, security, and versioning.

This skill is based on OpenAPI, Microsoft REST API Guidelines, and common REST API practice, but local project conventions take precedence where they already exist.

## When To Use

Use this skill when:

- Creating or modifying Spring MVC controllers.
- Designing request or response DTOs.
- Adding upload, pagination, search, admin, auth, or business CRUD endpoints.
- Writing or reviewing OpenAPI/Swagger annotations.
- Choosing HTTP methods, status codes, URL paths, or error codes.
- Checking whether an API response follows `Result<T>` and `PageResult<T>` conventions.
- Reviewing whether sensitive fields, tokens, API keys, or private file URLs could leak.

## Workflow

1. Identify the resource being operated on and name the URL with plural nouns.
2. Choose the HTTP method by resource semantics, not by Java method name.
3. Define request DTOs, path variables, query parameters, and validation rules.
4. Define response shape using the project response envelope.
5. Assign status code and business error code ranges.
6. Check authentication, authorization, sensitive field exposure, and logging risk.
7. Confirm pagination, sorting, filtering, and time formats are consistent.
8. Before finishing, run the checklist at the end of this skill.

## Project Defaults

- API base path should use `/api/v1`.
- Request and response fields should use camelCase.
- Standard success envelope should match `Result<T>`.
- Pagination responses should match `PageResult<T>`.
- Use Spring validation annotations for request DTO constraints.
- Keep business validation errors in typed exceptions, not ad hoc controller branches where possible.

## URL Standards

### Basic Format

```
https://api.example.com/v1/{资源名}/{资源ID}/{子资源}
```

**规则：**
- 使用小写字母和连字符 `-`（不用下划线 `_`）
- 资源名使用复数名词（如 `users`、`configs`）
- 版本号放在 URL 路径中（如 `/v1/`）
- 不使用文件扩展名（如 `.json`）

**正确示例：**
```
GET /api/v1/users
GET /api/v1/users/{id}
GET /api/v1/users/{id}/configs
POST /api/v1/llm/configs
PATCH /api/v1/llm/configs/{id}
DELETE /api/v1/llm/configs/{id}
```

**错误示例：**
```
GET /api/v1/getUser        # 使用了动词
GET /api/v1/user          # 单数名词
GET /api/v1/user/{id}/    # 尾部斜杠
GET /api/v1/user_configs  # 下划线
```

### Nested Resources

嵌套资源表示资源之间的包含关系：

```
GET /api/v1/users/{userId}/conversations
GET /api/v1/users/{userId}/conversations/{conversationId}/messages
```

**注意：** 嵌套层级不超过 3 层，避免 URL 过长。

## HTTP Method Standards

| 方法 | 用途 | 幂等性 | 副作用 |
|------|------|--------|--------|
| GET | 查询资源，不修改数据 | ✅ 幂等 | 无 |
| POST | 创建资源 | ❌ 非幂等 | 创建新资源 |
| PUT | 全量更新资源（替换） | ✅ 幂等 | 替换资源 |
| PATCH | 部分更新资源 | ❌ 非幂等 | 更新部分字段 |
| DELETE | 删除资源 | ✅ 幂等 | 删除资源 |

**CRUD 映射示例：**

| 操作 | 方法 | URL | 请求体 | 说明 |
|------|------|-----|--------|------|
| 获取用户列表 | GET | `/users` | - | 分页参数 |
| 获取单个用户 | GET | `/users/{id}` | - | - |
| 创建用户 | POST | `/users` | 用户信息 | 返回新资源 |
| 全量更新用户 | PUT | `/users/{id}` | 完整用户信息 | 替换 |
| 部分更新用户 | PATCH | `/users/{id}` | 部分字段 | 仅传需修改字段 |
| 删除用户 | DELETE | `/users/{id}` | - | 软删除或硬删除 |

## Status Code Standards

### Success Status Codes

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| 200 OK | 请求成功 | GET、PATCH、DELETE 成功 |
| 201 Created | 资源创建成功 | POST 创建新资源 |
| 202 Accepted | 请求已接收 | 异步操作 |
| 204 No Content | 无返回内容 | DELETE 成功无返回 |

### Client Error Status Codes

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| 400 Bad Request | 请求参数错误 | 参数校验失败、格式错误 |
| 401 Unauthorized | 未认证 | 未登录、Token 过期 |
| 403 Forbidden | 无权限 | 已登录但无权限 |
| 404 Not Found | 资源不存在 | ID 不合法或资源已删除 |
| 409 Conflict | 资源冲突 | 重复创建、数据冲突 |
| 422 Unprocessable Entity | 请求格式正确但语义错误 | 业务校验失败 |
| 429 Too Many Requests | 请求过于频繁 | 限流 |

### Server Error Status Codes

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| 500 Internal Server Error | 服务器内部错误 | 未捕获的异常 |
| 502 Bad Gateway | 网关错误 | 第三方服务不可用 |
| 503 Service Unavailable | 服务不可用 | 维护、宕机 |
| 504 Gateway Timeout | 网关超时 | 第三方服务超时 |

## Request Standards

### Headers

| 请求头 | 说明 | 示例 |
|--------|------|------|
| Content-Type | 请求体格式 | `application/json` |
| Authorization | 认证信息 | `Bearer {token}` |
| Accept | 可接受的响应格式 | `application/json` |

### Query Parameters

- 布尔值使用 `true`/`false`（不用 0/1）
- 日期格式使用 ISO 8601：`yyyy-MM-dd` 或 `yyyy-MM-ddTHH:mm:ss`
- 枚举值使用字符串

**分页参数规范：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码（从 1 开始） |
| pageSize | int | 20 | 每页条数 |
| sort | string | - | 排序字段，格式：`field:asc` 或 `field:desc` |

**示例：**
```
GET /api/v1/users?page=1&pageSize=20&sort=createdAt:desc
```

### Path Variables

- 参数名使用驼峰命名（如 `userId`、`configId`）
- 参数值必须 URL 编码
- 不存在可选路径参数

**示例：**
```
GET /api/v1/users/{userId}/configs/{configId}
```

## Response Standards

### Standard Response Envelope

**成功响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

**分页响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

**错误响应：**
```json
{
  "code": 10004,
  "message": "用户配置不存在",
  "data": null
}
```

### Field Naming

- **请求和响应字段**：使用小驼峰（camelCase）
- **枚举值**：使用小写下划线（如 `provider_type`）或小写连字符
- **时间字段**：使用 ISO 8601 格式，UTC 时间

**正确示例：**
```json
{
  "userId": 1,
  "configName": "我的配置",
  "createdAt": "2026-04-15T10:30:00Z"
}
```

**错误示例：**
```json
{
  "user_id": 1,
  "config-name": "我的配置",
  "created_at": "2026-04-15 10:30:00"
}
```

### Pagination Fields

分页响应必须包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| items | array | 数据列表 |
| total | long | 总记录数 |
| page | int | 当前页码 |
| pageSize | int | 每页条数 |

**可选字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| totalPages | int | 总页数 |
| hasNext | boolean | 是否有下一页 |
| hasPrevious | boolean | 是否有上一页 |

## Error Handling Standards

### Error Response Shape

```json
{
  "code": 10004,
  "message": "用户配置不存在",
  "detail": "配置ID: 12345 不存在于数据库",
  "timestamp": "2026-04-15T10:30:00Z",
  "path": "/api/v1/llm/configs/12345",
  "requestId": "uuid-trace-id"
}
```

### Error Code Ranges

| 错误码范围 | 分类 | 说明 |
|-----------|------|------|
| 10001-19999 | 业务错误 | 特定业务逻辑错误 |
| 20001-29999 | 用户/认证错误 | 用户相关、认证授权 |
| 30001-39999 | 资源错误 | 资源操作相关 |
| 40001-49999 | 参数错误 | 请求参数校验 |
| 50001-59999 | 系统错误 | 服务器、第三方服务 |

### Error Message Rules

- `message`：简短描述，适合用户阅读
- `detail`：详细说明，包含上下文信息
- 不在 message 中暴露敏感信息
- 不在 message 中使用英文

## Security Standards

### Authentication

| 方式 | 适用场景 | Header 格式 |
|------|----------|-------------|
| Bearer Token | API 认证 | `Authorization: Bearer {token}` |
| API Key | 服务间调用 | `X-API-Key: {api-key}` |

### Sensitive Data Handling

- **禁止**在 URL 中传递敏感信息（Token、密码等）
- **禁止**在日志中记录敏感信息
- **禁止**在响应中返回敏感信息（密码、完整 Token 等）
- 使用脱敏值返回（如 `sk-****....****`）

## Versioning Standards

### URL Versioning

```
/api/v1/users
/api/v2/users
```

### Version Upgrade Rules

- 旧版本至少维护 6 个月
- 在响应 Header 中添加 `API-Version` 字段
- breaking changes 必须发布新 major 版本

## REST Constraints

| 约束 | 说明 |
|------|------|
| 客户端-服务端分离 | 客户端与服务端独立，互不依赖 |
| 无状态 | 服务端不保存客户端状态 |
| 可缓存 | 响应可标记为可缓存或不可缓存 |
| 分层系统 | 客户端无需知道服务端架构 |
| 统一接口 | 通过 URL 定位资源，使用 HTTP 方法操作 |

## Checklist

生成 API 代码前，必须检查：

- [ ] URL 是否使用复数名词
- [ ] URL 是否使用小写和连字符
- [ ] HTTP 方法是否符合语义
- [ ] 状态码是否正确（201 创建、204 删除无内容等）
- [ ] 响应是否使用统一格式
- [ ] 字段命名是否使用小驼峰
- [ ] 分页响应是否包含必要字段
- [ ] 错误响应是否包含 code、message
- [ ] 是否对敏感信息脱敏
- [ ] 是否有必要的参数校验注解
