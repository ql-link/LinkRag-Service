# 功能信息卡

## 1. 基础信息

- 模块名称：缓存一致性改造
- 当前期次：二期
- 业务域：platform / llm-config / provider
- 当前状态：测试交付完成，待远端部署联调
- 复杂度等级：L3
- 当前分支：refactor/cache-consistency-cdc

## 2. 功能摘要

- 背景：一期已经完成项目级同步删缓存 + CDC/MQ 二次删除补偿框架，并落地 `user` 读写链路与 `provider`、`llm-config` 写链路；但 `provider`、`llm-config` 读链路仍未接入统一缓存读治理能力，缓存穿透、击穿、雪崩防护也尚未沉淀为通用复用模块。
- 目标：在二期将缓存读治理能力进一步抽象为项目级共享模块，并完成 `provider`、`llm-config` 两条读链路接入，形成“可复用读保护 + 业务 owner service + 写侧补偿一致性”闭环。
- 本期目标：
  - 抽象项目级共享缓存读治理模块，统一承载空值缓存、回源合并、TTL 抖动、key 回填、owner service 接入约定。
  - 完成 `provider` 读链路缓存接入。
  - 完成 `llm-config` 读链路缓存接入。
  - 输出 Canal 部署与接入说明文档，供后续生产部署和全链路落地使用。
- 本期不做：
  - 不执行 Canal 服务端实际部署与运维发布。
  - 不完成 DB -> Canal -> Kafka 的生产环境全链路联调。
  - 不新增 Python 侧改造或跨语言消费逻辑。
  - 不在本期引入异步更新缓存、缓存预热或多级缓存体系。

## 3. 影响范围

- 关联模块：
  - `link-components/toLink-components-redis`
  - `link-service`
  - `link-api`
  - `docs/模块开发文档/缓存一致性改造/二期`
- 关联中间件：
  - MySQL
  - Redis
  - MQ
  - Canal（文档层交付，非本期部署）
- 关联公共契约：
  - Redis key 命名与 TTL 约定
  - owner service 接入约定
  - 读链路缓存保护通用模式
  - 缓存补偿消息契约与 Canal 投递说明

## 4. 文档清单

- `feature_info.md`
- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`
- `canal_deployment.md`

## 5. 关联功能

- 一期已完成能力：
  - `CacheConsistencyService`
  - `CacheKeyRouter` / `CacheEvictTarget`
  - Kafka 缓存补偿消费者
  - `user` 域读写链路治理
  - `provider`、`llm-config` 写链路同步删缓存
- 二期首批接入对象：
  - `llm:pvd:{providerType}`
  - `llm:cfg:{configId}`
  - `llm:u_def:{userId}`
- 二期重点治理问题：
  - `provider`、`llm-config` 读链路尚未形成 owner service 闭环
  - 空值缓存、击穿保护、TTL 抖动仍偏向单业务实现
  - 未来新增缓存业务缺少统一复用模板
  - 远端 `llm_user_config` 仍需发布前迁移唯一索引，才能完整支持同模型多能力拆行

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `requirement.md`
3. `technical_design.md`
4. `AGENTS.md`
5. `project_info.md`
6. `docs/组件和数据库约定/middleware_contract.md`
7. `docs/组件和数据库约定/middleware-components/redis_component.md`
8. `docs/模块开发文档/缓存一致性改造/一期/technical_design.md`

## 7. 后续回填项

- 技术设计阶段待明确：
  - 共享单条查询模块最终类名与包结构
  - `provider`、`llm-config` 各自的 owner service 与批量补详情实现
  - 空值缓存、回源合并与 TTL 策略的统一参数口径
- 实现完成后回填：
  - 实际落地的共享模块范围：复用 `CacheReadProtectionService`，新增 `UserLLMConfigCacheService` 接入 `llm:cfg` 与 `llm:u_def`
  - 首批接入读接口清单：`UserLLMConfigService#getDefaultConfig(userId, capability)`、配置详情缓存读取
  - Canal 文档完成情况：已新增 `canal_deployment.md`，覆盖 MySQL binlog、Canal、CDC 转换桥、Kafka、Java 消费端、验证与回滚方案
  - 测试与遗留风险结论：`mvn -pl link-api -am test` 通过；远端 Canal / CDC 部署联调与 `llm_user_config` 索引迁移仍为发布前置项
