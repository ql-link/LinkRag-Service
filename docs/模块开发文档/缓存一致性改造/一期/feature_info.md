# 功能信息卡

## 1. 基础信息

- 模块名称：缓存一致性改造
- 当前期次：一期
- 业务域：platform / user / llm-config
- 当前状态：评审与质量门禁中
- 复杂度等级：L3
- 当前分支：refactor/cache-consistency-cdc

## 2. 功能摘要

- 背景：当前项目 Redis 一致性主要依赖应用侧双删策略，用户信息缓存已形成读写闭环，但 `llm-config`、系统厂商、默认配置等缓存仍以“预留驱逐入口”为主，缺少统一的项目级缓存一致性框架与 CDC 异步补偿链路。
- 目标：将项目缓存一致性方案升级为“写库后同步删除缓存 + CDC/Binlog 异步二次删除补偿”的统一框架，明确缓存接入标准、公共契约、消息边界和一期建设范围。
- 本期目标：
  - 定义项目级统一缓存一致性框架的业务边界和接入规则。
  - 明确一期仅建设公共框架，不批量迁移旧业务缓存。
  - 锁定写请求、CDC 事件、MQ 投递和二次删除补偿之间的职责边界。
  - 为二期新业务接入和旧业务缓存清洗提供统一准入标准。
- 本期不做：
  - 不在一期完成所有存量缓存迁移。
  - 不在一期引入异步更新缓存或缓存预热重建。
  - 不在一期改造与缓存无关的业务读写链路。
  - 不在一期承诺覆盖所有 Redis key，仅定义首批接入范围和排除范围。

## 3. 影响范围

- 关联模块：
  - `link-components/toLink-components-redis`
  - `link-components/toLink-components-mq`
  - `link-service`
  - `link-api`
- 关联中间件：
  - MySQL
  - Redis
  - MQ
- 关联公共契约：
  - Redis key 命名与一致性策略
  - MQ 事件命名、消息结构、消费者幂等边界
  - 可观测性与补偿处理约定

## 4. 文档清单

- `feature_info.md`
- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`

## 5. 关联功能

- 历史相似功能：
  - 用户信息缓存双删
  - 系统厂商与用户 LLM 配置缓存驱逐
- 一期依赖的上游能力：
  - MySQL 主数据写入
  - Redis 缓存读写
  - MQ 抽象组件
- 二期受影响功能：
  - `user:info:{userId}`
  - `user:role:{userId}`
  - `llm:cfg:{configId}`
  - `llm:u_def:{userId}`
  - `llm:pvd:{providerType}`

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `requirement.md`
3. `technical_design.md`
4. `AGENTS.md`
5. `project_info.md`
6. `docs/组件和数据库约定/middleware_contract.md`
7. `docs/组件和数据库约定/middleware-components/redis_component.md`
8. `docs/组件和数据库约定/middleware-components/kafka_component.md`

## 7. 实现完成后回填

- 一期已完成：
  - 统一同步删缓存执行器 `CacheConsistencyService`
  - 统一 key 路由 `CacheEvictTarget` + `CacheKeyRouter`
  - 缓存补偿消息模型与 Kafka 消费者
  - `user` 读链路读保护能力接入
  - `provider`、`llm-config` 写链路同步删缓存接入
  - 旧 `DoubleDeleteCacheService` 删除
  - Redis / MQ 公共契约改写
- 当前待完成：
  - `provider`、`llm-config` 读链路治理投入二期
  - 最终审核与发布前联调证据补齐
- 当前测试结论：
  - 一期已验证 `user` 读写链路与 `provider`、`llm-config` 写链路
  - 文档与公共契约已完成回填
  - 全量集成验证受本地 Redis / Kafka 环境限制，当前结论为“有条件可交付”
