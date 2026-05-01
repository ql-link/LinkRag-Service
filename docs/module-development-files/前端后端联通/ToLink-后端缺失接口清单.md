# ToLink 后端缺失接口清单

> 文档版本：1.0
> 创建日期：2026-04-27
> 适用范围：后端开发排期、前后端联调、需求评审
> 关联文档：[ToLink-前端API文档.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/ToLink-前端API文档.md)

---

## 一、背景说明

基于当前仓库代码实际实现情况，ToLink 已具备以下基础能力：

- 用户登录注册
- 用户资料管理
- LLM 配置管理
- 用量统计
- 数据集创建、列表、详情、删除
- 知识文件上传、列表、详情、解析任务创建、删除
- 对话创建、列表、历史消息查询、删除
- 管理员用户管理、厂商管理、知识文件配置管理

但如果目标是让前端完成“完整 AI 对话产品页”和“成熟文件管理页”，当前后端仍存在一批关键缺失接口。

本文档按“产品闭环缺口”而不是“代码层零散缺口”来整理，分为：

- AI 对话核心缺口
- 对话管理缺口
- 数据集与文件管理缺口
- 非接口但影响联调的重要能力缺口

---

## 二、当前缺口总览

### 2.1 P0 - 必须优先补齐

| 模块 | 缺失能力 | 建议接口 |
|------|----------|----------|
| AI 对话 | 发送消息并获得回复 | `POST /api/v1/chat/conversations/{conversationId}/messages` |
| AI 对话 | 流式回复 | `POST /api/v1/chat/chat` 或 `POST /api/v1/chat/conversations/{conversationId}/stream` |

### 2.2 P1 - 前端主流程强相关

| 模块 | 缺失能力 | 建议接口 |
|------|----------|----------|
| 对话管理 | 按数据集筛选对话 | `GET /api/v1/datasets/{datasetId}/conversations` 或 `GET /api/v1/chat/conversations?datasetId=xxx` |
| 对话管理 | 修改对话标题 | `PATCH /api/v1/chat/conversations/{conversationId}` |
| 对话管理 | 对话置顶/取消置顶 | `PATCH /api/v1/chat/conversations/{conversationId}` |
| 对话管理 | 清空对话消息 | `DELETE /api/v1/chat/conversations/{conversationId}/messages` |
| 文件管理 | 修改数据集 | `PATCH /api/v1/datasets/{datasetId}` |

### 2.3 P2 - 提升文件管理完整度

| 模块 | 缺失能力 | 建议接口 |
|------|----------|----------|
| 文件管理 | 重命名知识文件 | `PATCH /api/v1/knowledge-files/{fileId}` |
| 文件管理 | 前端下载原始知识文件 | `GET /api/v1/knowledge-files/{fileId}/download` |
| 文件管理 | 批量删除知识文件 | `DELETE /api/v1/knowledge-files` |
| 文件管理 | 查询解析进度/结果摘要 | `GET /api/v1/knowledge-files/{fileId}/parse-status` |
| 文件管理 | 批量重试解析 | `POST /api/v1/knowledge-files/parse-tasks/batch` |

---

## 三、AI 对话核心缺口

### 3.1 发送消息并获取 AI 回复（P0）

#### 3.1.1 推荐接口

```http
POST /api/v1/chat/conversations/{conversationId}/messages
```

#### 3.1.2 请求头

```http
satoken: {accessToken}
Content-Type: application/json
```

#### 3.1.3 请求体

```json
{
  "content": "用户输入的问题",
  "configId": 1
}
```

#### 3.1.4 请求参数说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 是 | 用户消息内容，不能为空 |
| configId | number | 否 | 指定使用的 LLM 配置 ID，不传则使用上次配置或默认配置 |

#### 3.1.5 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1001,
    "conversationId": 100,
    "role": "assistant",
    "content": "AI 回复内容...",
    "configId": 1,
    "modelName": "gpt-4",
    "tokenCount": 150,
    "createdAt": "2026-04-27T10:30:00"
  }
}
```

#### 3.1.6 后端职责

- 校验对话是否属于当前用户
- 保存用户消息，`role = user`
- 根据 `conversationId` 找到绑定的 `datasetId`
- 根据 `datasetId` 触发知识检索
- 根据 `configId` 或默认配置调用 LLM
- 保存 AI 回复消息，`role = assistant`
- 更新对话的 `lastConfigId`、`lastModelName`、`updatedAt`
- 写入用量日志

#### 3.1.7 缺失原因说明

当前代码里虽然已有：

- 对话实体 `ChatConversation`
- 消息实体 `ChatMessage`
- 请求 DTO `SaveMessageRequest`
- 历史消息查询接口

但没有公开 Controller 暴露“发送消息”能力，因此前端目前只能看历史消息，不能真正发起 AI 对话。

---

### 3.2 流式对话接口（P0）

#### 3.2.1 推荐接口方案 A

```http
POST /api/v1/chat/chat
```

#### 3.2.2 推荐接口方案 B

```http
POST /api/v1/chat/conversations/{conversationId}/stream
```

更推荐方案 B，因为它和现有“对话”模型更一致。

#### 3.2.3 请求体

```json
{
  "datasetId": 10001,
  "message": "用户问题",
  "configId": 1,
  "stream": true
}
```

如果使用带 `conversationId` 的路径版本，则 `datasetId` 可选或不传。

#### 3.2.4 参数说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| datasetId | number | 条件必填 | 新建会话或无上下文时用于知识检索 |
| message | string | 是 | 用户消息 |
| configId | number | 否 | LLM 配置 ID |
| stream | boolean | 否 | 是否流式返回，默认 false |

#### 3.2.5 非流式响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "完整回复内容",
    "conversationId": 100,
    "messageId": 1001
  }
}
```

#### 3.2.6 流式响应示例

```http
Content-Type: text/event-stream
```

```text
data: {"content":"部分","done":false}
data: {"content":"回复","done":false}
data: {"content":"内容","done":true}
```

#### 3.2.7 后端职责

- 通过 SSE 按 chunk 推送
- 最后一条消息返回 `done=true`
- 结束后统一落库完整 assistant 消息
- 失败时返回明确错误事件

#### 3.2.8 为什么必须补

当前 `UserLLMConfig` 里已经存在 `streamEnabled` 字段，但后端并没有提供任何流式接口，这意味着前端无法真正消费该能力。

---

## 四、对话管理缺口

### 4.1 按数据集展示对话列表（P1）

#### 4.1.1 推荐接口

方案 A：

```http
GET /api/v1/datasets/{datasetId}/conversations?page=1&pageSize=20
```

方案 B：

```http
GET /api/v1/chat/conversations?datasetId=10001&page=1&pageSize=20
```

更推荐方案 B，因为改动更小，也更兼容现有对话列表接口。

#### 4.1.2 为什么缺

当前 `GET /api/v1/chat/conversations` 只按：

- `userId`
- `isDeleted = false`

查询，没有 `datasetId` 过滤条件。

#### 4.1.3 价值

- 前端可以在某个数据集详情页直接展示“该数据集下的全部会话”
- 避免前端拉全部会话后再本地筛选

---

### 4.2 修改对话标题（P1）

#### 4.2.1 推荐接口

```http
PATCH /api/v1/chat/conversations/{conversationId}
```

#### 4.2.2 请求体

```json
{
  "title": "新对话标题"
}
```

#### 4.2.3 响应

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

#### 4.2.4 说明

- 所有字段部分更新
- 该接口建议和置顶能力复用同一个 PATCH

---

### 4.3 对话置顶/取消置顶（P1）

#### 4.3.1 推荐接口

```http
PATCH /api/v1/chat/conversations/{conversationId}
```

#### 4.3.2 请求体

```json
{
  "isPinned": true
}
```

#### 4.3.3 参数说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| isPinned | boolean | 是 | `true=置顶`，`false=取消置顶` |

#### 4.3.4 为什么应补

当前 `ConversationDTO` 已经返回 `isPinned`，对话列表也已按 `isPinned` 排序，但没有任何公开接口可以修改它，字段处于“只读摆设”状态。

---

### 4.4 清空对话消息（P1）

#### 4.4.1 推荐接口

```http
DELETE /api/v1/chat/conversations/{conversationId}/messages
```

#### 4.4.2 说明

- 清空指定对话下所有消息
- 对话本身保留
- 删除后不可恢复

#### 4.4.3 为什么应补

前端经常需要“保留会话壳子但重开对话”的能力，当前只有“删对话”，没有“清空消息”。

---

## 五、数据集与文件管理缺口

### 5.1 修改数据集（P1）

#### 5.1.1 推荐接口

```http
PATCH /api/v1/datasets/{datasetId}
```

#### 5.1.2 请求体

```json
{
  "name": "新数据集名称",
  "description": "新的数据集描述"
}
```

#### 5.1.3 参数说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 否 | 最大 128 字符 |
| description | string | 否 | 最大 512 字符 |

#### 5.1.4 规则建议

- 仅更新传入字段
- 同一用户下不允许重名

---

### 5.2 重命名知识文件（P2）

#### 5.2.1 推荐接口

```http
PATCH /api/v1/knowledge-files/{fileId}
```

#### 5.2.2 请求体

```json
{
  "originalFilename": "新的文件名.pdf"
}
```

#### 5.2.3 为什么应补

当前上传同名文件会直接报冲突：

- `当前数据集下已存在同名原文件，请先重命名后再上传`

但系统又没有提供重命名接口，用户只能删除重传，体验较差。

---

### 5.3 前端下载知识文件（P2）

#### 5.3.1 推荐接口

```http
GET /api/v1/knowledge-files/{fileId}/download
```

#### 5.3.2 为什么需要单独补

当前只有内部服务下载接口：

```http
GET /api/v1/internal/knowledge-files/{fileId}/content
```

它依赖：

```http
Authorization: Bearer {serviceToken}
```

这是内部解析服务用的，不适合前端直接访问。

#### 5.3.3 前端版下载接口职责

- 使用用户登录态鉴权
- 校验文件归属
- 返回附件下载流

---

### 5.4 批量删除知识文件（P2）

#### 5.4.1 推荐接口

```http
DELETE /api/v1/knowledge-files
```

#### 5.4.2 请求体

```json
{
  "fileIds": [1001, 1002, 1003]
}
```

#### 5.4.3 为什么应补

当前只有单文件删除接口，前端若要支持批量勾选删除，会产生大量串行请求。

---

### 5.5 查询解析进度/结果摘要（P2）

#### 5.5.1 推荐接口

```http
GET /api/v1/knowledge-files/{fileId}/parse-status
```

#### 5.5.2 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": 1001,
    "parseTaskId": "task-xxx",
    "parseStatus": "PROCESSING",
    "progress": 60,
    "failureReason": null,
    "updatedAt": "2026-04-27T11:00:00"
  }
}
```

#### 5.5.3 为什么应补

当前只能拿到较粗粒度的状态值：

- `NOT_STARTED`
- `PENDING`
- `PROCESSING`
- `SUCCESS`
- `FAILED`

但没有更细的解析进度信息，前端只能做轮询状态刷新，无法展示更真实的上传处理进度。

---

### 5.6 批量重试解析（P2）

#### 5.6.1 推荐接口

```http
POST /api/v1/knowledge-files/parse-tasks/batch
```

#### 5.6.2 请求体

```json
{
  "fileIds": [1001, 1002, 1003]
}
```

#### 5.6.3 为什么应补

当前只有单文件：

```http
POST /api/v1/knowledge-files/{fileId}/parse-tasks
```

如果一批文件投递失败，前端无法高效批量重试。

---

## 六、非接口但必须同步规划的能力缺口

### 6.1 AI 回复消息自动保存

这不是新增接口，但属于必须明确的行为约束：

- 前端调用一次“发送消息”接口
- 后端自动完成两条消息落库
  - 用户消息 `role = user`
  - AI 回复 `role = assistant`

不应要求前端分两次调用来自己维护消息一致性。

### 6.2 对话元信息自动更新

发送消息成功后，后端应自动维护：

- `lastConfigId`
- `lastModelName`
- `updatedAt`

否则对话列表页的排序和摘要会失真。

### 6.3 用量统计自动写入

AI 回复链路应同时落 `usage log`，否则：

- 用量统计页不完整
- 配置维度分析不准确

### 6.4 流式错误事件规范

如果未来支持 SSE，对外需要统一约定：

- chunk 事件格式
- done 事件格式
- error 事件格式

否则前端无法稳定消费。

---

## 七、建议实现顺序

### 7.1 第一阶段：补齐聊天闭环

1. `POST /api/v1/chat/conversations/{conversationId}/messages`
2. 聊天链路中的消息保存、用量统计、对话更新时间维护

### 7.2 第二阶段：补齐流式体验

1. `POST /api/v1/chat/chat` 或对话级 stream 接口
2. SSE 事件格式约定

### 7.3 第三阶段：补齐对话管理

1. 按数据集筛选对话
2. 修改标题
3. 置顶/取消置顶
4. 清空消息

### 7.4 第四阶段：补齐文件管理增强能力

1. 修改数据集
2. 重命名文件
3. 用户态下载文件
4. 批量删除与批量重试解析
5. 解析进度查询

---

## 八、统一约定

### 8.1 Token 头

所有前端业务接口统一使用：

```http
satoken: {accessToken}
```

不是：

```http
Authorization: Bearer {token}
```

### 8.2 错误格式

后端应继续保持当前统一响应格式：

```json
{
  "code": 400,
  "message": "用户输入不能为空",
  "data": null
}
```

### 8.3 前端友好的设计原则

- 单次用户动作尽量只调一个接口
- 后端负责完成必要的联动落库
- 避免让前端拼装业务一致性

---

## 九、结论

当前 ToLink 的“文件基础管理”已经具备可用雏形，但“AI 对话核心链路”仍未闭环。

从产品落地优先级看，最需要优先补齐的是：

1. 发送消息并获取 AI 回复
2. 流式对话接口
3. 按数据集查看对话
4. 对话编辑能力
5. 数据集与知识文件增强管理能力

如果这些接口补齐，前端就能从“只能展示静态管理页”升级到“真正可用的 AI 知识对话产品”。
