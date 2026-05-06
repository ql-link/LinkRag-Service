# ToLink Service LLM能力与默认配置一期改造报告

> **文档状态：** 实现完成，待测试交付
> **项目名称**：ToLink Service
> **模块名称**：LLM能力与默认配置（一期）
> **需求文档**：[requirement.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/LLM能力与默认配置/一期/requirement.md)
> **技术文档**：[technical_design.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/LLM能力与默认配置/一期/technical_design.md)
> **分支名称**：refactor/llm-capability-default-config
> **负责人：** Fang / Codex
> **最后更新时间：** 2026-05-06

---

## 1. 文档修订记录 (Change Log)

| 版本号 | 修改日期 | 修改内容简述 | 修改人 | 审核人 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-06 | 记录 LLM 能力配置、每能力默认、Redis 默认映射和 MySQL 字段改造的实际落地结果 | Fang / Codex | Fang |

---

## 2. 改造背景与目标 (Overview)

### 2.1 改造背景

本期属于 L3 改造，实际改动跨 `link-api`、`link-service`、`link-model`、`link-mapper`、MySQL 脚本和 Redis 缓存契约。需求与技术方案已明确采用“系统厂商目录不拆表，用户配置按能力拆行”的方案，因此实现阶段需要单独沉淀实际落地结果、缓存契约变化和测试关注点。

### 2.2 本次改造目标

- 支持用户按能力查询启用中的厂商与模型。
- 支持创建用户配置时校验模型能力，并按模型能力写入多条 `llm_user_config`。
- 支持同一用户每种能力设置一个默认配置。
- 将用户配置单条详情缓存和默认配置映射缓存接入现有读保护与同步删缓存链路。
- 将表结构变化写入 `docs/db/init.sql`，将 Redis/MySQL 公共语义写入 `middleware_contract.md`。

### 2.3 本报告适用范围

**本报告重点说明：**

- 实际改动清单
- 实际落地位置
- 与技术方案差异
- 风险与后续事项

**本报告不重复展开：**

- 完整需求背景
- 完整技术方案推演
- 完整测试执行细节

---

## 3. 实际改造清单 (Implementation Inventory)

### 3.1 模块改动清单

| 模块 | 改动类型 | 实际改动内容 | 备注 |
| :--- | :--- | :--- | :--- |
| `link-api` | 新增/修改 | 新增用户侧厂商模型查询 Controller；改造配置创建、列表、默认读取和默认设置接口 | 创建接口返回能力记录列表 |
| `link-service` | 新增/修改 | 新增能力解析服务和用户配置缓存 owner service；改造厂商能力过滤、配置创建、默认设置与默认读取链路 | Redis 读保护与同步删缓存复用现有组件 |
| `link-model` | 新增/修改 | 新增模型能力响应 DTO；`UserLLMConfig` 改为单能力字段；请求/响应 DTO 补充能力与自定义 API 地址 | 新增 `INVALID_MODEL_CAPABILITY` 错误码 |
| `link-mapper` | 新增/修改 | 新增 `SystemProviderMapper.xml`、`UserLLMConfigMapper.xml`，将本期查询与更新 SQL 下沉到 XML | 遵守持久层查询规范 |
| `docs/db` | 修改 | `llm_user_config` 从多能力 JSON 语义收敛为 `capability VARCHAR(32)` 单能力字段，并补充索引 | `init.sql` 已更新 |
| `docs/组件和数据库约定` | 修改 | 回写 LLM 能力字段、默认配置作用域、Redis key value 语义和 TTL/失效策略 | 满足中间件契约要求 |

### 3.2 文件与类改动清单

| 文件/类 | 改动类型 | 实际职责变化 | 备注 |
| :--- | :--- | :--- | :--- |
| `ProviderController` | 新增 | 提供 `GET /api/v1/llm/providers`，支持按能力查询用户可选厂商与模型 | 登录用户接口 |
| `ConfigController` | 修改 | 配置列表支持 `capability` 过滤；创建返回列表；新增按能力默认读取和设置 | 继续从 `AuthContext` 获取 userId |
| `LLMCapabilityService` / `LLMCapabilityServiceImpl` | 新增 | 解析 `supported_models`、校验能力值、兼容旧数组格式 | 旧数组默认按 `CHAT` 处理 |
| `UserLLMConfigServiceImpl` | 修改 | 创建配置按能力拆行，默认配置按 `user_id + capability` 处理，缓存按 owner service 失效 | 主业务链路 |
| `UserLLMConfigCacheServiceImpl` | 新增 | 封装 `llm:cfg:{configId}` 和 `llm:u_def:{userId}` 的读保护、回源与驱逐 | 默认映射缓存 value 使用 wrapper 对象 |
| `SystemProviderMapper.xml` | 新增 | 厂商按唯一键、启用列表等查询 SQL | 新增查询下沉 XML |
| `UserLLMConfigMapper.xml` | 新增 | 用户配置列表、默认映射、按能力清理默认等 SQL | 新增查询/更新下沉 XML |
| `docs/db/init.sql` | 修改 | 表结构中落地 `capability` 单能力字段、唯一索引和默认读取索引 | 用户明确要求写入 |
| `middleware_contract.md` | 修改 | 记录 LLM 能力与 Redis 默认映射公共契约 | 用户明确要求写入 |

### 3.3 接口与配置改动清单

| 类型 | 名称 | 改动说明 | 影响范围 |
| :--- | :--- | :--- | :--- |
| API | `GET /api/v1/llm/providers` | 新增用户侧查询启用厂商和模型，支持 `capability` 过滤 | 前端添加配置流程 |
| API | `GET /api/v1/llm/configs` | 新增 `capability` 可选过滤参数 | 用户配置列表 |
| API | `POST /api/v1/llm/configs` | 返回值从单个 DTO 调整为能力记录列表 | 前端需按数组处理 |
| API | `GET /api/v1/llm/configs/default` | 新增按能力查询默认配置 | 下游选择默认模型 |
| API | `PATCH /api/v1/llm/configs/{id}/default` | 新增按能力设置默认配置 | 用户默认配置管理 |

### 3.4 数据与中间件改动清单

| 组件 | 名称/Key/Topic/Path | 改动说明 | 是否涉及契约更新 |
| :--- | :--- | :--- | :--- |
| MySQL | `llm_user_config.capability` | 单条用户配置记录只绑定一个能力；唯一维度调整为 `user_id + provider_id + model_name + capability` | 是 |
| MySQL | `idx_user_capability_default` | 支持按用户、能力、启用状态、默认状态读取默认配置 | 是 |
| Redis | `llm:cfg:{configId}` | 单条配置缓存，TTL 1 天，复用读保护与同步删缓存 | 是 |
| Redis | `llm:u_def:{userId}` | 默认配置映射缓存，业务语义为 `capability -> configId`，当前序列化对象为 `{"configIds":{...}}` | 是 |
| MQ | 无 | 本期不新增 MQ topic 或 consumer | 否 |
| OSS | 无 | 本期不涉及 OSS 路径或 bucket | 否 |

---

## 4. 实际落地实现说明 (What Was Built)

### 4.1 核心实现路径

用户查询可用模型时，经 `ProviderController -> SystemProviderService -> SystemProviderMapper.xml -> LLMCapabilityService` 返回启用厂商与能力过滤后的模型列表。

用户创建配置时，经 `ConfigController -> UserLLMConfigServiceImpl` 读取启用厂商、解析模型支持能力、校验入参能力，然后为每个能力写入一条 `llm_user_config`。如果请求要求设为默认，则在同一事务内按每个能力清理旧默认，并驱逐默认映射缓存。

用户读取默认配置时，先通过 `llm:u_def:{userId}` 获取用户默认映射，未命中时从 MySQL 回源重建映射，再按能力定位 `configId` 并读取 `llm:cfg:{configId}`。

### 4.2 关键实现点

| 实现点 | 实际落地位置 | 处理说明 |
| :--- | :--- | :--- |
| 能力目录解析 | `LLMCapabilityServiceImpl` | 支持推荐 JSON 对象格式，并兼容旧数组格式与测试中的 JSON 字符串根节点 |
| 用户配置拆行 | `UserLLMConfigServiceImpl#createConfig` | 根据模型支持能力生成多条配置记录；每条记录写入一个 `capability` |
| 每能力默认 | `UserLLMConfigMapper.xml#clearDefaultByUserIdAndCapability` | 同事务按 `userId + capability` 清理旧默认 |
| 默认映射缓存 | `UserLLMConfigCacheServiceImpl` | `llm:u_def:{userId}` 缓存全部能力默认关系，减少多能力读取时的 DB 查询 |
| 持久层边界 | `SystemProviderMapper.xml`、`UserLLMConfigMapper.xml` | 新增查询和动态条件均下沉 XML |
| 初始化脚本 | `docs/db/init.sql` | `llm_user_config` 表结构已改为 `capability VARCHAR(32)` |
| 公共契约 | `middleware_contract.md` | 回写 MySQL 能力字段、Redis key、TTL 和一致性策略 |

### 4.3 关键注释与设计意图

- 新增 Controller、Service、缓存 owner service 均补充中文类级注释，说明接口边界和缓存责任。
- `UserLLMConfigCacheServiceImpl` 注释说明 `llm:u_def:{userId}` 保持 key 不变，但 value 变更为能力默认映射。
- 业务实现通过方法拆分表达“能力校验、重复检查、默认清理、缓存驱逐”等关键边界，降低后续继续接入 chat/dataset 的理解成本。

### 4.4 未纳入实现的部分

- 未新增系统模型表、能力表或默认关系表。
- 未改造 Python 执行端读取默认配置的逻辑。
- 未实现 Redis 列表/索引缓存，列表查询仍以 MySQL 过滤主键和结构化字段为准。
- 未新增 Canal 部署脚本；本期只保证 Java 侧 key 语义可被 CDC 补偿删除识别。

---

## 5. 与技术方案差异说明 (Delta From Technical Design)

### 5.1 差异清单

| 技术方案项 | 原方案 | 实际实现 | 差异原因 | 是否已重新确认 |
| :--- | :--- | :--- | :--- | :--- |
| 默认映射 Redis value 示例 | 文档示例为 `{"CHAT":10001}` | 实际缓存对象为 `{"configIds":{"CHAT":10001}}` | `CacheReadProtectionService` 当前按 `Class<T>` 反序列化，使用 wrapper 对象更稳妥 | 是 |
| 厂商模型 JSON 兼容 | 推荐 `{"model":["CHAT"]}` | 同时兼容旧数组 `["gpt-4"]`，默认按 `CHAT` | 保持历史测试和旧数据可用 | 是 |
| 创建接口返回 | 返回创建结果 | 实际返回 `List<UserLLMConfigDTO>` | 与按能力拆行事实一致，前端可直接获得多能力记录 | 是 |

### 5.2 差异影响分析

- 对上游调用的影响：前端创建配置接口需要按数组读取 `data`。
- 对下游模块的影响：chat/dataset 后续应统一调用 `getDefaultConfig(userId, capability)`，旧 `getDefaultConfig(userId)` 仅保留 CHAT 过渡语义。
- 对测试范围的影响：API 测试需要按数组断言创建结果；缓存测试需理解默认映射 wrapper 对象。
- 对发布与回滚的影响：表结构从多能力 JSON 收敛为单能力字段，生产发布前需要单独准备历史数据迁移脚本或重建策略。

---

## 6. 风险、遗留问题与后续事项 (Risks & Follow-up)

### 6.1 当前已知风险

- `llm_user_config` 表结构变更涉及历史数据迁移，当前仓库已更新建表脚本，但生产环境仍需单独执行迁移方案。
- `llm:u_def:{userId}` 复用旧 key 但改变 value 语义，发布时建议删除旧默认配置缓存，避免旧值反序列化失败或语义错读。
- 同一用户同一能力仅一个默认依赖事务内清理旧默认和普通索引支撑，当前未通过数据库唯一约束硬性限制 `is_default=true` 的唯一性。

### 6.2 遗留问题

- Python 执行端尚未按能力读取默认配置。
- 管理端维护 `supported_models` 的交互和校验还可以进一步增强。
- Canal 部署文档仍需在测试交付阶段补充，供后续环境部署使用。

### 6.3 后续建议动作

- 测试阶段补充按能力查询厂商、创建多能力配置、设置默认、读取默认和缓存失效验证。
- 发布前准备生产数据迁移 SQL：旧 `capabilities` JSON 拆分成多条 `capability` 记录。
- 二期联动 chat/dataset 时，统一以 `CHAT`、`EMBEDDING` 等能力值读取默认配置。

---

## 7. 回写检查 (Update Checklist)

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填实现摘要 | 是 | 状态、分支、实现回填项已更新 |
| `middleware_contract.md` 已按需更新 | 是 | 已记录 LLM 能力字段与 Redis 默认映射契约 |
| `project_info.md` 已按需更新 | 否 | 测试交付阶段统一更新 |
| 已通知测试阶段关注实现差异 | 是 | 见本报告风险和后续事项 |
