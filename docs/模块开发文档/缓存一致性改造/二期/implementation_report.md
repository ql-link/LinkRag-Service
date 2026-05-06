# ToLink Service 缓存一致性改造二期改造报告

> **文档状态：** 实现完成，待远端部署联调
> **项目名称**：ToLink Service
> **模块名称**：缓存一致性改造（二期）
> **需求文档**：[requirement.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/二期/requirement.md)
> **技术文档**：[technical_design.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/二期/technical_design.md)
> **部署文档**：[canal_deployment.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/二期/canal_deployment.md)
> **分支名称**：refactor/cache-consistency-cdc
> **负责人：** Fang / Codex
> **最后更新时间：** 2026-05-06

---

## 1. 文档修订记录 (Change Log)

| 版本号 | 修改日期 | 修改内容简述 | 修改人 | 审核人 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-06 | 补齐二期实际落地结果、方案偏差、远端表结构差异与交付风险 | Fang / Codex | Fang |

---

## 2. 改造背景与目标 (Overview)

### 2.1 改造背景

缓存一致性一期已经建立 `CacheConsistencyService`、`CacheKeyRouter`、`CacheEvictTarget` 和 `tolink.cache.evict` Kafka 补偿消费者，完成了写库成功后同步删缓存和 CDC/MQ 二次删除补偿的 Java 侧框架。

二期原目标是在该框架之上补齐 `provider` 与 `llm-config` 读链路治理，统一复用空值缓存、单 key 回源合并与 TTL 抖动能力，并沉淀 Canal / CDC 部署文档。实际推进过程中，`llm-config` 读链路接入与 LLM 能力配置模块合并落地，因此本报告同时说明二期自身产物和与 `LLM能力与默认配置/一期` 的交叉落地关系。

### 2.2 本次改造目标

- 明确 `CacheReadProtectionService` 作为共享读保护能力继续复用，不再重构底层框架。
- 完成 `llm-config` 配置详情与默认配置映射的 owner service 接入。
- 回写 Redis / MySQL 公共契约，明确 `llm:cfg:{configId}`、`llm:u_def:{userId}` 的 TTL、value 语义与失效策略。
- 输出 Canal / CDC 部署文档，明确 MySQL binlog、Canal、CDC 转换桥、Kafka、Java 消费者与 Redis 删除链路。
- 核对远端表结构，识别发布前必须处理的迁移差异。

### 2.3 本报告适用范围

**本报告重点说明：**

- 二期实际落地的代码、文档与契约
- 与技术方案存在的偏差
- 当前远端表结构差异
- 发布、联调和回滚风险

**本报告不重复展开：**

- 一期缓存一致性框架完整设计
- LLM 能力配置模块完整业务需求
- Canal / CDC 真实生产部署执行记录

---

## 3. 实际改造清单 (Implementation Inventory)

### 3.1 模块改动清单

| 模块 | 改动类型 | 实际改动内容 | 备注 |
| :--- | :--- | :--- | :--- |
| `link-components/toLink-components-redis` | 复用 | 继续复用 `CacheReadProtectionService`、`CacheConsistencyService`、`CacheKeyRouter` | 二期未重构底层 Redis framework |
| `link-service` | 新增/修改 | 新增 `UserLLMConfigCacheService`，封装 `llm:cfg` 与 `llm:u_def` 读保护和驱逐 | 随 LLM 能力配置实现提交落地 |
| `link-api` | 修改 | 用户配置接口返回与按能力默认读取配套调整 | 主要归属 LLM 能力配置模块 |
| `link-mapper` | 新增/修改 | 新增 `UserLLMConfigMapper.xml`、`SystemProviderMapper.xml`，支撑按能力查询与默认读取 | 新查询下沉 XML |
| `docs/组件和数据库约定` | 修改 | 回写 Redis key、TTL、默认配置映射、MySQL 能力字段约定 | 已更新 `middleware_contract.md` |
| `docs/模块开发文档/缓存一致性改造/二期` | 新增/修改 | 补齐二期需求、技术方案、Canal 部署文档、实现报告、测试交付记录 | 本次收口 |

### 3.2 文件与类改动清单

| 文件/类 | 改动类型 | 实际职责变化 | 备注 |
| :--- | :--- | :--- | :--- |
| `UserLLMConfigCacheService` | 新增 | 定义用户 LLM 配置缓存 owner service 接口 | 管理配置详情和默认映射 |
| `UserLLMConfigCacheServiceImpl` | 新增 | 通过 `CacheReadProtectionService` 读取 `llm:cfg:{configId}` 与 `llm:u_def:{userId}` | 默认映射缓存使用 wrapper 对象 |
| `UserLLMConfigServiceImpl` | 修改 | 创建、更新、删除、设置默认后统一驱逐配置详情和默认映射缓存 | 写侧继续同步删缓存 |
| `CacheCompensationKafkaReceiver` | 复用 | 继续消费 `tolink.cache.evict` | 一期已落地 |
| `CacheCompensationMQReceiver` | 复用 | 将补偿消息转为 `CacheConsistencyService.evictCompensation` | 一期已落地 |
| `canal_deployment.md` | 新增 | 说明 Canal / CDC 部署、转换桥消息契约、验证与回滚 | 本期文档交付 |
| `middleware_contract.md` | 修改 | 记录 LLM 能力字段、Redis 默认映射 key 和一致性策略 | 公共契约回写 |

### 3.3 接口与配置改动清单

| 类型 | 名称 | 改动说明 | 影响范围 |
| :--- | :--- | :--- | :--- |
| Service | `UserLLMConfigService#getDefaultConfig(userId, capability)` | 按能力读取默认配置，优先走默认映射缓存 | chat/dataset 后续可复用 |
| Service | `UserLLMConfigCacheService#getConfigOrLoad` | 配置详情缓存读取与 DB 回源 | `llm-config` 读链路 |
| Service | `UserLLMConfigCacheService#getDefaultConfigIdMapOrLoad` | 用户能力默认映射缓存读取与 DB 回源 | 默认配置读链路 |
| Kafka | `tolink.cache.evict` | 继续作为缓存补偿删除 topic | Canal / CDC 转换桥投递 |
| 配置 | `tolink.cache-consistency.consumer.group-id` | 默认 `tolink-cache-evict` | Java 补偿消费者 |

### 3.4 数据与中间件改动清单

| 组件 | 名称/Key/Topic/Path | 改动说明 | 是否涉及契约更新 |
| :--- | :--- | :--- | :--- |
| Redis | `llm:cfg:{configId}` | 单条用户 LLM 配置缓存，TTL 1 天，读保护接入 | 是 |
| Redis | `llm:u_def:{userId}` | 用户按能力默认配置映射缓存，TTL 1 天 | 是 |
| Kafka | `tolink.cache.evict` | 继续承载缓存补偿事件 | 是，沿用一期 |
| MySQL | `llm_user_config.capability` | 用户配置按单能力拆行 | 是，随 LLM 能力配置落地 |
| Canal | `canal_deployment.md` | 只交付部署文档，不在本期执行真实部署 | 是，文档层 |

---

## 4. 实际落地实现说明 (What Was Built)

### 4.1 核心实现路径

写路径仍沿用一期范式：

```text
业务写库成功
    -> CacheConsistencyService.evict(...)
    -> CacheKeyRouter.route(...)
    -> Redis DEL
    -> 后续 Canal / CDC 消息再次触发 evictCompensation
```

`llm-config` 默认配置读路径实际落地为：

```text
UserLLMConfigServiceImpl#getDefaultConfig(userId, capability)
    -> UserLLMConfigCacheService#getDefaultConfigIdMapOrLoad
    -> Redis llm:u_def:{userId}
    -> miss 时 MySQL selectDefaultsByUserId
    -> 取出 capability 对应 configId
    -> UserLLMConfigCacheService#getConfigOrLoad
    -> Redis llm:cfg:{configId}
    -> miss 时 MySQL selectByIdAndUserId
```

Canal / CDC 部署链路文档化为：

```text
MySQL binlog
    -> Canal Server
    -> CDC 转换桥
    -> Kafka topic tolink.cache.evict
    -> Java Consumer group tolink-cache-evict
    -> CacheKeyRouter
    -> Redis DEL
```

### 4.2 关键实现点

| 实现点 | 实际落地位置 | 处理说明 |
| :--- | :--- | :--- |
| 共享读保护能力复用 | `CacheReadProtectionService` | 直接复用空值缓存、回源合并、TTL 抖动 |
| 用户配置详情缓存 | `UserLLMConfigCacheServiceImpl#getConfigOrLoad` | key 为 `llm:cfg:{configId}` |
| 用户默认映射缓存 | `UserLLMConfigCacheServiceImpl#getDefaultConfigIdMapOrLoad` | key 为 `llm:u_def:{userId}`，业务语义为 `capability -> configId` |
| 写路径驱逐 | `UserLLMConfigServiceImpl` | 创建、更新、删除、设置默认后删除配置详情和默认映射 |
| CDC 消息契约 | `canal_deployment.md` | 明确扁平 JSON 字段和表到 `cache_target` 的映射 |
| 远端结构核对 | MySQL `SHOW CREATE TABLE` | 已识别 `llm_user_config` 唯一索引仍需迁移 |

### 4.3 关键注释与设计意图

- `UserLLMConfigCacheServiceImpl` 注释说明 `llm:u_def:{userId}` key 不变，但 value 语义变为按能力默认映射。
- `CacheCompensationMQ` 注释说明 Canal 或 CDC 桥只转换数据库变更事实，Java 消费端只做二次删缓存，不重建缓存。
- `canal_deployment.md` 明确要求 CDC 转换桥存在，避免误把 Canal 原始消息直接投递给 Java。

### 4.4 未纳入实现的部分

- 未新增独立 `SystemProviderCacheService` owner service；`system_provider` 写侧同步删与 CDC 补偿能力已存在，读侧 owner service 可后续按同一模板补。
- 未落地“列表查询 DB 查主键 + Redis 批量补详情”的完整批量实现；当前核心完成在单对象读保护和 LLM 默认配置映射。
- 未执行真实 Canal Server、CDC 转换桥和 Kafka 远端部署。

---

## 5. 与技术方案差异说明 (Delta From Technical Design)

### 5.1 差异清单

| 技术方案项 | 原方案 | 实际实现 | 差异原因 | 是否已重新确认 |
| :--- | :--- | :--- | :--- | :--- |
| Provider 读链路 owner service | 新增 `SystemProviderCacheService` | 未单独新增，仍主要保留写侧驱逐和服务层读取 | 本轮重心转到 LLM 能力配置与默认配置读链路 | 是 |
| 列表批量补详情 | DB 查 ID + Redis 批量补详情 | 未完整落地批量补详情框架 | 当前数据规模和接口优先级下先保证单对象缓存与默认映射 | 是 |
| Canal 部署 | 输出部署说明文档 | 已输出 `canal_deployment.md`，但未真实部署 | 用户明确要求只完成 Java 侧和文档，部署后续执行 | 是 |
| LLM 配置读保护 | 二期独立落地 | 随 `LLM能力与默认配置/一期` 一并实现 | 两个模块在 `llm-config` 领域交叉，合并实现降低重复改动 | 是 |

### 5.2 差异影响分析

- 对上游调用的影响：无新增破坏性 API；LLM 配置创建接口按能力拆行返回数组，已在 LLM 模块文档说明。
- 对下游模块的影响：后续 chat/dataset 应按能力调用默认配置读取，不再依赖用户全局默认。
- 对测试范围的影响：已通过 `link-api` 集成测试覆盖用户、配置、缓存 mock 修复和 LLM 配置主链路；真实 CDC 仍需联调测试。
- 对发布与回滚的影响：远端 `llm_user_config` 唯一索引必须先迁移，否则同模型多能力拆行会被旧唯一索引阻塞。

---

## 6. 远端表结构核对结果

2026-05-06 已连接远端 `36.213.180.176:3306/tolink_rag_db` 核对关键表结构。

### 6.1 `llm_user_config`

| 项 | 远端现状 | 当前代码/脚本期望 | 影响 |
| :--- | :--- | :--- | :--- |
| `capability` 字段 | 已存在，`NOT NULL DEFAULT 'CHAT'` | 已存在，`NOT NULL` | 可兼容 |
| 唯一索引 | `uk_user_provider_model(user_id, provider_id, model_name)` | `uk_user_provider_model_capability(user_id, provider_id, model_name, capability)` | 必须迁移，否则多能力拆行失败 |
| 默认读取索引 | `idx_user_active_default(user_id,is_active,is_default)` | `idx_user_capability_default(user_id,capability,is_active,is_default)` | 建议迁移，影响按能力默认查询效率 |
| 列表索引 | `idx_user_provider_cap(user_id,provider_type,capability)` | `idx_user_provider_active(user_id,provider_type,is_active)` | 建议补充，影响列表筛选效率 |

建议迁移 SQL：

```sql
ALTER TABLE llm_user_config DROP INDEX uk_user_provider_model;

ALTER TABLE llm_user_config
  ADD UNIQUE KEY uk_user_provider_model_capability
  (user_id, provider_id, model_name, capability);

ALTER TABLE llm_user_config
  ADD KEY idx_user_capability_default
  (user_id, capability, is_active, is_default);

ALTER TABLE llm_user_config
  ADD KEY idx_user_provider_active
  (user_id, provider_type, is_active);
```

### 6.2 `llm_system_provider`

- 表结构与当前脚本基本一致。
- 远端实际 `supported_models` 数据仍有旧数组格式，例如 `["gpt-4"]`。
- 当前 Java 代码兼容旧数组并按 `CHAT` 处理，但若要支持 `EMBEDDING/OCR/VISION` 等能力查询，应逐步改为 `{"modelName":["CHAT"]}` 结构。

### 6.3 文件解析历史表

- 远端仍存在 `document_parse_task`、`knowledge_file_config` 等历史表。
- 当前脚本使用 `document_parse_log`，且 `knowledge_file_config` 已退出 MySQL 主链路。
- 这属于文件上传与解析重构历史迁移范围，不阻塞本期 LLM 配置缓存一致性，但若要远端整体对齐当前仓库，需要单独处理。

---

## 7. 风险、遗留问题与后续事项 (Risks & Follow-up)

### 7.1 当前已知风险

- 远端 `llm_user_config` 旧唯一索引会阻塞同模型多能力拆行。
- Canal / CDC 转换桥未部署，生产级二次删除补偿闭环尚未真实验证。
- `llm:u_def:{userId}` 复用旧 key 但 value 语义变化，发布时建议清理旧 key。

### 7.2 遗留问题

- `SystemProviderCacheService` 可在后续迭代补齐，形成与 `UserLLMConfigCacheService` 完全一致的 owner service 模板。
- 列表查询批量补详情模式尚未作为通用模板完全落地。
- 远端历史文件解析表与当前脚本存在差异，需要文件上传解析模块单独迁移。

### 7.3 后续建议动作

- 发布 LLM 能力配置前，先执行 `llm_user_config` 索引迁移并验证无重复数据冲突。
- 部署 Canal / CDC 转换桥后，按 `canal_deployment.md` 做 Kafka 直投、默认配置映射和系统厂商缓存删除验证。
- 将 `llm_system_provider.supported_models` 逐步迁移为模型到能力列表的 JSON 对象。

---

## 8. 回写检查 (Update Checklist)

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填实现摘要 | 是 | 已更新当前状态与实现回填 |
| `middleware_contract.md` 已按需更新 | 是 | 已记录 LLM 配置 Redis key、TTL、默认映射与 MySQL 能力字段 |
| `project_info.md` 已按需更新 | 是 | 已记录 LLM 能力配置与默认映射现状 |
| 已通知测试阶段关注实现差异 | 是 | 见远端表结构和风险章节 |
