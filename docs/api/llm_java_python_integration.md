# LLM 配置 Java-Python 对接说明

更新日期：2026-06-04

本文说明当前 Java 管理端对 LLM 配置的表结构、接口、缓存同步和加密契约。目标读者是 Python RAG 端开发者，用于调整实际 LLM 调用时的配置读取逻辑。

## 1. 当前职责边界

Java 管理端负责：

- 维护系统厂商配置 `llm_system_provider`。
- 维护用户 LLM 配置 `llm_user_config`。
- 加密写入用户 API Key。
- 为前端临时代理拉取模型列表，结果不落库。
- 用户配置新增、更新、设置默认、删除后，通过 MQ 通知 Python 清理缓存。

Python RAG 端负责：

- 实际执行 LLM 调用。
- 按 `X-User-Id`、`config_id`、`capability` 从 MySQL 读取用户配置。
- 解密 `llm_user_config.api_key`。
- 当用户没有个人默认配置时，回退读取 `llm_user_config.user_id=0` 的系统预设配置。
- Python 端环境变量 `SYSTEM_LLM_*` 只作为运维应急兜底，不作为产品主路径。
- 消费 Java 发送的缓存同步 MQ，清理 Redis 配置缓存和 ModelFactory 客户端缓存。

## 2. 表结构关键变化

### 2.1 `llm_system_provider`

系统厂商表现在只记录厂商能力，不再记录模型名。

关键字段：

| 字段 | 含义 |
| --- | --- |
| `id` | 厂商 ID，用户配置表通过 `provider_id` 关联 |
| `provider_type` | 厂商类型，如 `openai` / `anthropic` / `glm` / `deepseek` / `qwen` |
| `provider_name` | 厂商展示名 |
| `api_base_url` | 厂商默认 API base URL |
| `supported_capabilities` | JSON 数组，只记录能力列表 |
| `config_schema` | 厂商配置 schema，包含前端模型列表拉取规则 |
| `is_active` | 是否启用 |
| `priority` | 展示排序优先级 |

`supported_models` 已废弃，Python ORM 和读取逻辑需要改为 `supported_capabilities`，或者做短期兼容。

示例：

```json
["CHAT", "EMBEDDING", "OCR", "VISION", "RERANK"]
```

合法能力词汇表以 Java 当前实现为准：

```text
CHAT / EMBEDDING / OCR / VISION / REASONING / CODE / TOOL_CALLING / RERANK
```

实际 Python 调用路径目前主要使用：

```text
CHAT / EMBEDDING / RERANK / OCR
```

### 2.2 `llm_user_config`

用户配置表一条记录只对应一个能力维度和一个用户选择的模型。

`user_id=0` 是系统预设配置保留用户 ID，不代表真实用户。系统预设行用于保证新注册用户不配置 LLM 也能直接使用基础能力；普通用户可以选择系统预设，但不能修改、删除或查看其 API Key、模型名、base URL 等细节。

Python 实际调用 LLM 时需要保持正确读取的字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 配置 ID，即 Python 请求体里的可选 `config_id` |
| `user_id` | 用户 ID，来自请求头 `X-User-Id` |
| `provider_id` | 关联 `llm_system_provider.id` |
| `provider_type` | 厂商类型快照，Python 应优先使用该字段构造 provider |
| `provider_name` | 厂商名称快照 |
| `api_key` | AES-256-GCM 密文 |
| `custom_api_base_url` | 用户自定义 base URL，可为空 |
| `model_name` | 用户最终选择或手动填写的模型名 |
| `capability` | 当前配置对应的能力 |
| `is_active` | 是否启用 |
| `is_default` | 是否为该能力默认配置 |
| `timeout_ms` | 调用超时时间 |
| `max_retries` | 最大重试次数 |
| `stream_enabled` | 是否启用流式 |
| `extra_config` | 扩展配置 JSON |

Java 侧会校验：

- `capability` 必填，并归一化为大写。
- 厂商必须启用。
- 厂商 `supported_capabilities` 必须包含用户选择的 `capability`。
- 不校验 `model_name` 是否真的匹配该能力，这个判断交给用户和上游厂商。
- 同一用户、同一厂商、同一模型、同一能力不能重复。
- 同一用户、同一能力最多只能有一条 `is_active=true AND is_default=true` 的配置。

默认配置唯一性由 MySQL 生成列实现：

```sql
default_capability VARCHAR(32) GENERATED ALWAYS AS (
    CASE WHEN is_active = TRUE AND is_default = TRUE THEN capability ELSE NULL END
) STORED
```

以及唯一键：

```sql
UNIQUE KEY uk_user_default_capability (user_id, default_capability)
```

Python 不需要写入或读取 `default_capability`，它只是数据库约束辅助列。

## 3. Python 读取配置的规则

### 3.1 请求入参

Python LLM 接口需要使用以下上下文：

| 入参 | 来源 | 用途 |
| --- | --- | --- |
| `user_id` | Header `X-User-Id` | 限定用户配置归属 |
| `config_id` | 请求体，可选 | 指定配置优先 |
| `capability` | Python 路由推导 | 未传 `config_id` 时查该能力默认配置 |

当前能力映射：

| Python 接口 | capability |
| --- | --- |
| `/api/v1/llm/generate` | `CHAT` |
| `/api/v1/llm/generate/stream` | `CHAT` |
| `/api/v1/llm/embed` | `EMBEDDING` |
| `/api/v1/llm/rerank` | `RERANK` |
| `/api/v1/llm/ocr` | `OCR` |

### 3.2 传了 `config_id`

按指定配置查询：

```sql
SELECT *
FROM llm_user_config
WHERE id = :config_id
  AND (user_id = :user_id OR user_id = 0)
  AND is_active = TRUE;
```

建议 Python 端拿到配置后，仍校验：

- `capability` 是否符合当前接口期望；如果不一致，应拒绝或按现有错误模型返回。
- `api_key` 解密成功。
- `model_name` 非空。
- 如果命中 `user_id=0`，仍用请求头里的真实 `X-User-Id` 作为调用用户上下文和用量归属；`user_id=0` 只表示配置来源。

### 3.3 没传 `config_id`

按能力查默认配置：

```sql
SELECT *
FROM llm_user_config
WHERE user_id = :user_id
  AND capability = :capability
  AND is_active = TRUE
  AND is_default = TRUE
LIMIT 1;
```

数据库唯一约束会保证同一用户同一能力最多只有一条启用默认配置。

### 3.4 用户未配置时

如果用户没有对应能力的个人默认配置，Python 端需要继续查系统预设：

```sql
SELECT *
FROM llm_user_config
WHERE user_id = 0
  AND capability = :capability
  AND is_active = TRUE
  AND is_default = TRUE
LIMIT 1;
```

Java 当前不在用户注册时复制配置到用户名下，也不会为每个用户生成默认配置。平台需要在 `llm_user_config` 中至少初始化两条 `user_id=0` 系统预设：

```text
user_id=0 + capability=CHAT + is_active=true + is_default=true
user_id=0 + capability=EMBEDDING + is_active=true + is_default=true
```

如果系统预设也不存在，Python 可以继续使用环境变量作为最后应急兜底：

```text
SYSTEM_LLM_PROVIDER
SYSTEM_LLM_API_KEY
SYSTEM_LLM_API_BASE
SYSTEM_LLM_MODEL_CHAT
SYSTEM_LLM_MODEL_EMBEDDING
SYSTEM_LLM_MODEL_RERANK
SYSTEM_LLM_MODEL_VISION
```

`OCR` 兜底仍可复用 `SYSTEM_LLM_MODEL_VISION`。

## 4. API Key 加密契约

Java 写入 `llm_user_config.api_key` 时使用 AES-256-GCM，必须与 Python 解密保持一致。

Java 配置项：

```text
tolink.llm.api-key.secret
```

Python 配置项：

```text
API_KEY_ENCRYPTION_SECRET
```

两端值必须一致。

算法规则：

```text
算法：AES/GCM/NoPadding
密钥来源：secret UTF-8 bytes
AES key：前 32 字节；不足 32 字节右侧补 0
IV：随机 12 字节
AAD：None
明文：api_key UTF-8 bytes
密文格式：base64(IV + ciphertext_with_gcm_tag)
GCM tag：16 字节，包含在 Java Cipher.doFinal 返回值尾部
```

Python 等价逻辑：

```python
key = API_KEY_ENCRYPTION_SECRET.encode()[:32].ljust(32, b"\0")
raw = base64.b64decode(encrypted)
iv = raw[:12]
ciphertext_with_tag = raw[12:]
api_key = AESGCM(key).decrypt(iv, ciphertext_with_tag, None).decode()
```

## 5. Java 用户侧接口

这些接口主要服务前端和管理端，不是 Python 实际调用 LLM 的入口，但它们决定了入库数据形态。

### 5.1 查询可选厂商

```http
GET /api/v1/llm/providers?capability=CHAT
```

返回启用且支持该能力的厂商：

```json
{
  "code": 200,
  "data": [
    {
      "providerId": 10000,
      "providerType": "openai",
      "providerName": "OpenAI",
      "apiBaseUrl": "https://api.openai.com/v1",
      "supportedCapabilities": ["CHAT", "EMBEDDING"]
    }
  ]
}
```

### 5.2 临时拉取模型列表

```http
POST /api/v1/llm/providers/{providerId}/models
Content-Type: application/json

{
  "apiKey": "sk-xxxxx",
  "customApiBaseUrl": "https://api.openai.com/v1"
}
```

说明：

- Java 后端按 `llm_system_provider.config_schema.modelFetch` 代理请求上游模型列表。
- API Key 只用于本次请求，不落库。
- 请求失败时前端应提示，并允许用户手动填写模型名。
- 模型列表结果只用于前端展示，Python 不依赖这份列表。

返回：

```json
{
  "code": 200,
  "data": {
    "allowManualInput": false,
    "models": [
      {
        "id": "gpt-4o",
        "displayName": "gpt-4o",
        "ownedBy": "openai"
      }
    ]
  }
}
```

如果厂商不支持拉取或拉取失败，前端可以走手动输入流程。

### 5.3 创建用户配置

```http
POST /api/v1/llm/configs
Content-Type: application/json

{
  "providerId": 10000,
  "providerType": "openai",
  "configName": "我的 OpenAI Chat",
  "apiKey": "sk-xxxxx",
  "customApiBaseUrl": null,
  "modelName": "gpt-4o",
  "capability": "CHAT",
  "isDefault": true,
  "timeoutMs": 60000,
  "maxRetries": 3,
  "streamEnabled": true,
  "extraConfig": null
}
```

返回单条配置对象，不再返回多能力数组：

```json
{
  "code": 200,
  "data": {
    "id": 10001,
    "configName": "我的 OpenAI Chat",
    "providerType": "openai",
    "providerName": "OpenAI",
    "modelName": "gpt-4o",
    "capability": "CHAT",
    "apiKeyMasked": "****",
    "customApiBaseUrl": null,
    "priority": 50,
    "isActive": true,
    "isDefault": true
  }
}
```

### 5.4 查询和设置默认配置

```http
GET /api/v1/llm/configs/default?capability=CHAT
PATCH /api/v1/llm/configs/{id}/default?capability=CHAT
```

查询默认配置时，Java 先查当前用户个人默认配置；查不到时返回 `user_id=0` 系统预设摘要。系统预设 DTO 会脱敏：

```json
{
  "id": 10000,
  "configName": "System Default Chat",
  "providerType": "qwen",
  "providerName": "Qwen",
  "modelName": null,
  "apiKeyMasked": null,
  "customApiBaseUrl": null,
  "capability": "CHAT",
  "isActive": true,
  "isDefault": true,
  "systemPreset": true,
  "editable": false,
  "selectable": true,
  "extraConfig": null
}
```

设置个人配置为默认时，Java 只清理同一 `user_id + capability` 下其他配置的 `is_default`。

设置系统预设为默认时，Java 不修改 `user_id=0` 行，只清空当前用户同 capability 下的个人默认配置。Python 后续按“用户默认优先，系统预设兜底”规则自然命中系统预设。

普通用户更新或删除系统预设配置会返回：

```text
SYSTEM_PRESET_READONLY(10017/403)
```

## 6. 模型列表拉取配置

模型列表路径和鉴权规则放在 `llm_system_provider.config_schema.modelFetch`，由 Java 后端执行，不放在前端。

结构示例：

```json
{
  "modelFetch": {
    "enabled": true,
    "method": "GET",
    "urlTemplate": "{baseUrl}/models",
    "auth": {
      "type": "bearer"
    },
    "headers": {
      "anthropic-version": "2023-06-01"
    },
    "response": {
      "itemsPath": "data",
      "idPath": "id",
      "displayNamePath": "display_name",
      "ownedByPath": "type"
    }
  }
}
```

当前 Java 限制：

- 只支持 `GET`。
- `urlTemplate` 只能使用 `{baseUrl}`，不能包含 `{apiKey}`。
- 默认不允许 HTTP，默认阻断私网地址。
- 不跟随重定向。
- 最大返回模型数默认 `500`。
- 日志不打印 API Key。

Java 配置项：

```yaml
tolink:
  llm:
    model-fetch:
      connect-timeout-ms: 3000
      read-timeout-ms: 10000
      call-timeout-ms: 15000
      allow-http: false
      block-private-address: true
      max-models: 500
```

## 7. 缓存一致性协定

Java 用户配置变更后，会在数据库事务提交后发送 MQ：

```text
queue/topic: tolink.rag.cache_sync
direction: Java -> Python
```

消息体：

```json
{
  "user_id": "123",
  "config_id": "10001",
  "action": "refresh"
}
```

系统预设配置变更时，Java 可发送：

```json
{
  "user_id": "0",
  "config_id": null,
  "action": "refresh"
}
```

`user_id=0` 在 cache-sync 中表示系统级配置变化信号，不代表真实调用用户。

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `user_id` | 是 | 发生配置变更的用户 |
| `config_id` | 删除时必填；刷新时通常携带 | 变更的配置 ID |
| `action` | 是 | `refresh` 或 `invalidate` |

发送时机：

| Java 操作 | action |
| --- | --- |
| 创建用户配置 | `refresh` |
| 更新用户配置 | `refresh` |
| 设置默认配置 | `refresh` |
| 删除用户配置 | `invalidate` |

Python 消费后应清理：

- 指定配置缓存，例如 `llm:user:{user_id}:config:{config_id}`。
- 用户默认配置缓存，例如 `llm:user:{user_id}:default:{capability}` 这一类按能力默认缓存。
- 用户配置列表缓存，例如 `llm:user:{user_id}:configs:{capability}`。
- ModelFactory / provider client 实例缓存。

当收到 `user_id=0` 时，Python 应清理所有用户 LLM 配置缓存、系统 provider 缓存以及全部 ModelFactory client 缓存。原因是任意真实用户在没有个人默认配置时都可能回退到系统预设，无法只按单个用户收敛。

当前 Python 消费映射可以保持：

```text
refresh -> update
invalidate -> delete
```

Java 端发送失败时只记录错误日志，不回滚用户配置事务。因此 Python 端仍应保留 Redis TTL 作为最终兜底。

## 8. Python 端本版需要配合检查的点

1. ORM 字段：`SystemProviderDB.supported_models` 需要改为 `supported_capabilities`，或短期兼容新旧字段。
2. 系统厂商读取：返回结构里的 `supported_models` 应替换为 `supported_capabilities`；Python 实际调用不应依赖系统表模型名。
3. 用户配置读取：传 `config_id` 时按 `id + is_active=true + (user_id=当前用户 OR user_id=0)` 查。
4. 默认配置读取：不传 `config_id` 时先按 `user_id + capability + is_active=true + is_default=true` 查个人默认；查不到再按 `user_id=0 + capability + is_active=true + is_default=true` 查系统预设。
5. 能力归一化：Python 查询前统一大写 `capability`。
6. 解密密钥：确认 Python `API_KEY_ENCRYPTION_SECRET` 与 Java `tolink.llm.api-key.secret` 完全一致。
7. 缓存同步：确认消费者订阅 `tolink.rag.cache_sync`，且消费失败会走现有 MQ 重试/死信机制；收到 `user_id=0` 时清全量 LLM 配置缓存和全部 client 缓存。
8. 旧方法：无能力维度的 `get_user_default_config(user_id)` 不作为本版 LLM 路由对接依据；实际接口应使用按能力查询。
9. 兜底配置：用户未配置时优先使用 `user_id=0` 系统预设，环境变量只作为系统预设缺失时的最后应急兜底。

## 9. 最小对接验收

Python 端完成后，建议至少验证：

- 用户未配置 LLM 时，`CHAT` 和 `EMBEDDING` 能走 `user_id=0` 系统预设。
- 用户创建 `CHAT` 默认配置后，`/generate` 使用该用户配置。
- 用户创建 `EMBEDDING` 默认配置后，`/embed` 使用该用户配置。
- 请求体传个人 `config_id` 时优先使用指定配置。
- 请求体传系统预设 `config_id` 时可使用该配置，但调用用户上下文仍为真实 `X-User-Id`。
- `config_id` 不属于当前 `X-User-Id` 时不能被使用。
- 用户切回系统预设后，Java 只清空个人默认，Python 后续调用回退到 `user_id=0`。
- 用户更新 API Key 或模型名后，Java 发送 `refresh`，Python 下一次调用不再使用旧缓存。
- 用户删除配置后，Java 发送 `invalidate`，Python 不再使用已删除配置。
- Java 发送 `user_id=0 + action=refresh` 后，Python 清理全量 LLM 配置缓存和 client 缓存。
- Python ORM 不再读取已删除的 `llm_system_provider.supported_models` 列。
