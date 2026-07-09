# LLM 配置模块

本文档描述 Java 管理端的 LLM 厂商、模型能力、用户配置、系统预设和有效配置解析。

## 模块边界

代码入口：

| 职责 | 入口 |
| --- | --- |
| 用户侧配置 | `UserLLMConfigServiceImpl` |
| 有效配置解析 | `EffectiveLLMConfigServiceImpl` |
| 管理端厂商 | `SystemProviderServiceImpl` |
| 管理端模型能力 | `ProviderModelServiceImpl` |
| 系统预设 | `SystemPresetServiceImpl` |
| HTTP 入口 | `ConfigController`、`ProviderController`、`AdminController` |

核心表：

| 表 | 作用 |
| --- | --- |
| `llm_system_provider` | 系统厂商目录 |
| `llm_provider_model` | 厂商模型能力目录，一条记录对应一个模型能力 |
| `llm_user_config` | 用户自己的模型配置和默认选择 |
| `llm_system_preset` | LinkRag 系统兜底配置 |

## 用户配置流程

`POST /api/v1/llm/configs/setup-provider` 以厂商为单位生成用户配置：

1. 校验厂商存在且启用。
2. 读取该厂商已启用的模型能力目录。
3. 按 `(model_name, capability)` 为用户生成或更新 `llm_user_config`。
4. 将用户提交的厂商级 API Key 加密保存到每条用户配置。
5. `protocol`、`api_base_url`、能力类型来自模型能力目录；不会回退到厂商默认值。

用户侧 `toggle-model` 只切换自己的 `llm_user_config.enabled`。管理端启停厂商或模型能力属于系统目录变更，会影响后续有效配置解析。

## 有效配置解析

有效配置用于 Java 端调用模型前确定真实调用参数。解析顺序：

1. 优先读取当前用户在该 capability 下启用且设为默认的 `llm_user_config`。
2. 如果用户没有可用默认配置，回退到 LinkRag 系统预设。
3. 返回结果标记来源：`USER` 或 `SYSTEM`。
4. 对外响应中的 API Key 必须脱敏；实际调用侧使用解密后的密钥。

`configId` 可能指向用户配置，也可能指向系统预设。需要结合来源字段判断，不要只用数字 ID 推断配置类型。

## 管理端目录

管理端通过 `/api/v1/admin/providers`、`/api/v1/admin/provider-models` 维护系统目录：

- 厂商负责名称、标识、图标、启用状态等基础信息。
- 模型能力负责模型名、能力类型、协议、接口地址、上下文长度、启用状态。
- 系统预设负责 LinkRag 兜底配置。

模型能力是一等配置项。同一个模型可以有多个能力记录，例如 `chat`、`embed`、`rerank`。

## 缓存与一致性

LLM 配置相关服务会在用户配置、系统预设、厂商或模型能力变更时清理相关缓存。新增改动时要确认：

- 用户配置变更后，有效配置缓存失效。
- 系统预设变更后，系统兜底缓存失效。
- 厂商或模型能力停用后，用户侧不应继续解析到不可用能力。

## 修改注意事项

- 不要把 LinkRag 系统预设当成用户可编辑配置。
- 不要在用户配置中保存明文 API Key。
- 新增 capability 时，需要同步模型能力目录、有效配置解析和用量上报口径。
- API 契约变更需同步 `docs/api/api_contracts.md`。
- 表结构变更需同步 `docs/api/mysql_schema.md` 和 `scripts/db/init.sql`。
