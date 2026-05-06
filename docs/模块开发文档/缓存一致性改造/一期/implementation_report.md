# ToLink Service 缓存一致性改造一期改造报告

> **文档状态：** 草稿
> **项目名称**：ToLink Service
> **模块名称**：缓存一致性改造（一期）
> **需求文档**：[requirement.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/一期/requirement.md)
> **技术文档**：[technical_design.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/一期/technical_design.md)
> **分支名称**：refactor/cache-consistency-cdc
> **负责人：** Fang / Codex
> **最后更新时间：** 2026-05-06

---

## 1. 文档修订记录 (Change Log)

| 版本号 | 修改日期 | 修改内容简述 | 修改人 | 审核人 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-06 | 记录一期实际落地代码、公共契约变化与测试边界 | Fang / Codex | Fang |

## 2. 改造背景与目标 (Overview)

### 2.1 改造背景

本次改造属于 L3 跨模块中间件治理，实际实现同时涉及 Redis framework、业务缓存 owner service、MQ 消息模型、Kafka 消费者与公共契约回写，因此需要单独记录落地结果。

### 2.2 本次改造目标

- 将旧 `DoubleDeleteCacheService` 下线为统一同步删缓存执行器。
- 建立 `CacheEvictTarget -> CacheKeyRouter -> CacheConsistencyService` 的项目级删缓存入口。
- 新增 `CacheReadProtectionService`，把空值缓存、单 key 回源合并、TTL 抖动沉到 framework。
- 新增缓存补偿 MQ 模型与 Kafka 消费者，承接 Canal -> MQ -> Redis 的二次删除链路。
- 完成 `user`、`provider`、`llm-config` 首批写路径接入。

## 3. 实际改造清单 (Implementation Inventory)

### 3.1 模块改动清单

| 模块 | 改动类型 | 实际改动内容 | 备注 |
| :--- | :--- | :--- | :--- |
| `link-api` | 修改 | `StpInterfaceImpl` 改为走读保护缓存接口；测试环境补充缓存一致性配置 | 只改读链路与测试配置 |
| `link-service` | 新增/修改 | 改造 `UserCacheServiceImpl`、`AuthServiceImpl`、`AdminProviderServiceImpl`、`UserLLMConfigServiceImpl`；新增缓存补偿 MQ 模型与接收器 | 业务写路径统一接入 |
| `link-model` | 修改 | 新增 `CACHE_DELETE_FAILED` 错误码 | 统一主请求失败口径 |
| `link-components` | 新增/修改/删除 | 新增缓存一致性配置、路由、执行器、读保护；删除旧双删服务 | 一期核心落地点 |

### 3.2 文件与类改动清单

| 文件/类 | 改动类型 | 实际职责变化 | 备注 |
| :--- | :--- | :--- | :--- |
| `CacheConsistencyProperties` | 新增 | 提供 600ms 同步删预算、空值 TTL、TTL 抖动、回源等待时间配置 | Redis framework |
| `CacheEvictTarget` | 新增 | 定义统一逻辑缓存目标 | 同步删与 MQ 补偿共用 |
| `CacheKeyRouter` | 新增 | 统一逻辑目标到 Redis key 的映射 | 支持用户联合删除 |
| `CacheConsistencyService` | 新增 | 统一同步删缓存与异步补偿删缓存执行入口 | 主请求与消费者共用 |
| `CacheReadProtectionService` | 新增 | 提供空值缓存、单 key 回源合并、TTL 抖动 | 已接入 `user` 读链路 |
| `DoubleDeleteCacheService` | 删除 | 旧“同步删 + 延迟第二删”实现废弃 | 与方案一致 |
| `CacheCompensationMQ` | 新增 | 定义缓存补偿消息体 `event_id/cache_target/route_id/...` | 扁平 JSON |
| `CacheCompensationKafkaReceiver` | 新增 | Kafka 侧接入缓存补偿消费者 | group 默认 `tolink-cache-evict` |

### 3.3 接口与配置改动清单

| 类型 | 名称 | 改动说明 | 影响范围 |
| :--- | :--- | :--- | :--- |
| 配置项 | `tolink.cache-consistency.*` | 新增缓存一致性统一配置前缀 | Redis framework / 测试环境 |
| 消费者 | `tolink.cache.evict` | 新增缓存补偿 topic | Canal / Kafka / Java 消费端 |
| 消费组 | `tolink-cache-evict` | 新增缓存补偿消费者组默认值 | Kafka |

### 3.4 数据与中间件改动清单

| 组件 | 名称/Key/Topic/Path | 改动说明 | 是否涉及契约更新 |
| :--- | :--- | :--- | :--- |
| Redis | `user:info:*`、`user:role:*`、`llm:cfg:*`、`llm:u_def:*`、`llm:pvd:*` | 删除策略统一切到同步删 + CDC 二次删 | 是 |
| MQ | `tolink.cache.evict` | 新增缓存补偿消息主题与扁平消息结构 | 是 |

## 4. 实际落地实现说明 (What Was Built)

### 4.1 核心实现路径

写路径侧实际链路为：业务 Service 更新 MySQL -> `CacheConsistencyService` 同步删缓存。  
读路径侧当前优先接通 `user` 域：`AuthServiceImpl` / `StpInterfaceImpl` -> `UserCacheService#getOrLoad` -> `CacheReadProtectionService`。  
异步补偿侧实际落地为：Canal 产出事件后投递 `tolink.cache.evict` -> `CacheCompensationKafkaReceiver` -> `CacheCompensationMQReceiver` -> `CacheConsistencyService`。

### 4.2 关键实现点

| 实现点 | 实际落地位置 | 处理说明 |
| :--- | :--- | :--- |
| 同步删缓存 | `CacheConsistencyService` | 主请求默认 600ms 内快速重试，超时按配置决定是否失败 |
| 联合删除路由 | `CacheKeyRouter` | `USER` 目标一次删除 `user:info` 与 `user:role` |
| 读保护 | `CacheReadProtectionService` | 已用于用户资料与角色读取链路 |
| provider key 统一 | `AdminProviderServiceImpl` | 更新/删除/启停全部按 `providerType` 删除 `llm:pvd` |
| llm-config 驱逐联动 | `UserLLMConfigServiceImpl` | 配置更新/删除同时驱逐 `llm:cfg` 与 `llm:u_def` |

### 4.3 未纳入实现的部分

- 未在一期内落地 Canal 部署端代码。
- 未在一期内接入 `knowledge:file-upload:config`。
- 未引入消费幂等表、异步更新缓存或缓存预热。

## 5. 与技术方案差异说明 (Delta From Technical Design)

### 5.1 差异清单

| 技术方案项 | 原方案 | 实际实现 | 差异原因 | 是否已重新确认 |
| :--- | :--- | :--- | :--- | :--- |
| 一期业务迁移边界 | 更偏框架搭建 | 额外把 `user` 读链路接到了读保护能力 | 需要至少有一条真实链路验证框架不是空壳 | 是 |
| 同步删失败策略 | 默认主请求失败 | 增加 `sync-delete-required` 开关，生产默认仍为 true，测试环境可关闭 | 兼容本地/集成测试无 Redis 场景 | 是 |

### 5.2 差异影响分析

- 对上游调用的影响：业务层不再依赖旧双删类，统一走新执行器。
- 对下游模块的影响：Canal 或其他 CDC 桥只需按新消息结构投递 `tolink.cache.evict`。
- 对测试范围的影响：需要额外关注无 Redis / 无 Kafka 的测试环境隔离。
- 对发布与回滚的影响：可通过关闭 `sync-delete-required` 或停止新 topic 消费来减轻故障影响，但不再保留旧双删代码回滚。

## 6. 风险、遗留问题与后续事项 (Risks & Follow-up)

### 6.1 当前已知风险

- `link-api` 现有全量测试依赖本地 Redis/Kafka，当前沙箱下无法稳定跑完整套件。
- `CacheReadProtectionService` 当前只在 `user` 域形成真实接入，其他缓存 owner service 还需二期迁移。

### 6.2 后续建议动作

- 二期继续迁移 `llm-config` 读链路，补齐 owner service 闭环。
- 为 Canal -> MQ 桥增加部署文档和验收脚本。
- 在测试阶段补齐 Redis/Kafka 测试替身或测试容器方案。

## 7. 回写检查 (Update Checklist)

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填实现摘要 | 是 | 已更新当前状态与本期结果 |
| `middleware_contract.md` 已按需更新 | 是 | Redis / MQ 契约已改写 |
| `project_info.md` 已按需更新 | 否 | 待测试与交付阶段统一更新 |
| 已通知测试阶段关注实现差异 | 是 | 本报告已记录验证边界 |
