# Python -> Java 解析结果通知对接文档

## 1. 文档目的

本文档用于约定 Python 解析端在收到 `parse_task` 消息并完成解析后，如何把解析进度和最终结果通知给 Java 端。

当前链路职责固定为：

- Java 负责：
  - 受理解析请求
  - 更新 `document_parsed_file.latest_parse_task_id`
  - 发送 MQ
  - 接收 Python 内部回调
  - 通过 SSE 推送前端
- Python 负责：
  - 消费 `parse_task`
  - 创建并更新 `document_parse_log`
  - 更新 `document_parsed_file`
  - 最后回调 Java 内部接口通知进度与终态

## 2. 总体时序

Python 端在处理一次解析任务时，应按以下顺序与 Java 协作：

1. 收到 `parse_task`
2. 创建 `document_parse_log`
3. 开始解析时回调 `processing`
4. 解析过程中按每 1 秒回调一次 `progress`
5. 解析结束后，先写数据库终态
6. 成功或失败都要先更新 `document_parse_log`
7. 成功或失败都要再更新 `document_parsed_file`
8. 最后回调 Java 内部接口通知 `success` 或 `failed`

关键原则：

- 先写库，后通知 Java
- Java 收到回调后，只做事件转发，不替代 Python 写库
- 数据库是最终真相，SSE 是实时通知通道

## 3. 回调接口

### 3.1 接口信息

- 方法：`POST`
- 路径：`/api/v1/internal/parse-tasks/{taskId}/events`
- 说明：`{taskId}` 必须使用本次解析任务的业务唯一标识，与 MQ 消息中的 `task_id` 保持一致

示例：

```text
POST /api/v1/internal/parse-tasks/9f6b7d7e-4e7b-4a3f-9f4d-8d2a1b6c7e90/events
```

### 3.2 鉴权方式

必须使用服务间 Bearer Token：

```http
Authorization: Bearer {service_token}
Content-Type: application/json
```

说明：

- 不能使用用户登录态
- 不能把 token 放到 query 参数中
- token 由 Java 端配置 `tolink.knowledge-file.service-token`

### 3.3 响应结构

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

常见失败响应：

```json
{
  "code": 401,
  "message": "服务鉴权失败",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "解析进度必须在 0 到 100 之间",
  "data": null
}
```

```json
{
  "code": 404,
  "message": "解析任务不存在",
  "data": null
}
```

## 4. 请求体定义

请求体结构如下：

```json
{
  "eventType": "progress",
  "progress": 80,
  "failureReason": null
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `eventType` | String | 是 | 事件类型：`processing` / `progress` / `success` / `failed` |
| `progress` | Integer | 否 | 解析进度百分比，仅 `progress` 事件有效，取值范围 `0-100` |
| `failureReason` | String | 否 | 失败原因，仅 `failed` 事件有效 |

## 5. 事件约定

### 5.1 `processing`

使用场景：

- Python 已创建 `document_parse_log`
- Python 即将开始真正解析

示例：

```json
{
  "eventType": "processing"
}
```

Java 侧效果：

- 转发给前端 SSE
- 前端状态显示为 `解析中`

### 5.2 `progress`

使用场景：

- Python 正在解析中
- 需要同步当前进度

频率要求：

- 按每 1 秒上报一次

示例：

```json
{
  "eventType": "progress",
  "progress": 65
}
```

约束：

- `progress` 必须在 `0-100` 之间
- 不要求每 1% 都上报
- 断线或上报失败时，不要求补发历史进度

Java 侧效果：

- 解析状态按 `processing` 处理
- 前端状态保持 `解析中`
- 前端仅更新进度条

### 5.3 `success`

使用场景：

- Python 已完成成功写库
- `document_parse_log` 已更新为 `success`
- `document_parsed_file` 已完成成功侧更新

示例：

```json
{
  "eventType": "success"
}
```

Java 侧效果：

- 转发给前端 SSE
- 前端状态切换为 `解析成功`

### 5.4 `failed`

使用场景：

- Python 已完成失败写库
- `document_parse_log` 已更新为 `failed`
- `document_parsed_file` 已完成失败侧更新

示例：

```json
{
  "eventType": "failed",
  "failureReason": "PARSE_TIMEOUT"
}
```

Java 侧效果：

- 转发给前端 SSE
- 前端状态切换为 `解析失败`

## 6. 数据库更新顺序要求

### 6.1 成功场景

Python 必须按以下顺序执行：

1. 更新 `document_parse_log.task_status = success`
2. 写入解析产物定位、开始时间、结束时间、耗时等字段
3. 更新 `document_parsed_file`
4. 成功时执行 `parse_count + 1`
5. 最后回调 Java `success`

### 6.2 失败场景

Python 必须按以下顺序执行：

1. 更新 `document_parse_log.task_status = failed`
2. 写入 `failure_reason`、结束时间、耗时等字段
3. 更新 `document_parsed_file`
4. 失败时 `parse_count` 不增加
5. 最后回调 Java `failed`

说明：

- 不能先通知 Java，再写数据库
- 否则前端可能先收到终态，但查库时仍看到旧状态

## 7. Java 端处理方式

Java 端当前行为如下：

- 校验 Bearer Token
- 校验 `progress` 是否在 `0-100`
- 按 `taskId` 查询 `document_parse_log`
- 再反查对应原文件
- 生成 SSE 事件并按文件维度推送给前端

Java 不会在回调接口中：

- 写 `document_parse_log`
- 写 `document_parsed_file`
- 重新判定解析是否成功

## 8. 失败与重试建议

### 8.1 回调失败处理建议

如果回调 Java 失败：

- Python 不要把已成功的解析任务改写为失败
- 应保留数据库终态不变
- 可以记录本地日志，便于后续排查

### 8.2 Java 端兜底策略

即使回调失败，前端最终仍可通过查库拿到终态：

- 页面刷新时查库
- SSE 断线重连时查库
- 长时间停留在 `解析中` 且未收到终态时查库

所以：

- 回调接口负责“实时通知”
- 数据库负责“最终真相”

## 9. 推荐调用样例

### 9.1 开始解析

```bash
curl -X POST "http://{java-host}/api/v1/internal/parse-tasks/{taskId}/events" \
  -H "Authorization: Bearer {service_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "processing"
  }'
```

### 9.2 上报进度

```bash
curl -X POST "http://{java-host}/api/v1/internal/parse-tasks/{taskId}/events" \
  -H "Authorization: Bearer {service_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "progress",
    "progress": 80
  }'
```

### 9.3 通知成功

```bash
curl -X POST "http://{java-host}/api/v1/internal/parse-tasks/{taskId}/events" \
  -H "Authorization: Bearer {service_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "success"
  }'
```

### 9.4 通知失败

```bash
curl -X POST "http://{java-host}/api/v1/internal/parse-tasks/{taskId}/events" \
  -H "Authorization: Bearer {service_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "failed",
    "failureReason": "PARSE_TIMEOUT"
  }'
```

## 10. 对接结论

Python 通知 Java 的最终要求可总结为一句话：

**先写 `document_parse_log`，再写 `document_parsed_file`，最后调用 `/api/v1/internal/parse-tasks/{taskId}/events` 通知 Java。**
