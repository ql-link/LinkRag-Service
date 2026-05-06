# ToLink Service 缓存一致性改造一期测试执行与交付记录

> **文档状态：** 执行中
> **项目名称**：ToLink Service
> **模块名称**：缓存一致性改造
> **当前期次**：一期
> **需求文档**：[requirement.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/一期/requirement.md)
> **技术文档**：[technical_design.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/一期/technical_design.md)
> **改造报告**：[implementation_report.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/一期/implementation_report.md)
> **分支名称**：refactor/cache-consistency-cdc
> **执行人：** Codex
> **最后更新时间：** 2026-05-06

---

## 1. 使用说明

本文件用于指导一期缓存一致性改造的验证与交付判断。

当前状态说明：

- 已完成代码实现与文档回填
- 已完成一轮命令级验证与局部单测验证
- 受当前本地沙箱环境限制，未完成依赖真实 Redis / Kafka 的全量集成测试

## 2. 测试范围与目标

### 2.1 本次要验证的内容

- 项目级统一同步删缓存执行器是否替代旧双删实现
- `CacheEvictTarget` 与 `CacheKeyRouter` 是否覆盖一期首批 key 路由
- `user` 读链路是否接入缓存穿透、击穿、雪崩防护
- 缓存补偿 MQ 模型与 Kafka 接收器是否落地
- 业务写路径是否切换到新缓存一致性执行器
- 公共契约和组件说明文档是否与代码一致

### 2.2 本次不验证的内容

- Canal 部署端是否成功监听 MySQL binlog
- 真实 Kafka broker 上的端到端消费连通性
- 真实 Redis 环境下的 600ms 同步删失败重试表现
- 二期业务迁移范围内的所有旧缓存读链路

### 2.3 验收项映射

| 验收项 | 对应用例编号 | 是否覆盖 | 备注 |
| :--- | :--- | :--- | :--- |
| 写库成功后同步删除缓存 | TC-01、TC-E04 | 是 | 代码级已覆盖，真实 Redis 环境待联调 |
| CDC 异步二次删除补偿 | TC-02 | 部分 | 消息模型与消费者已验证，真实 broker 待联调 |
| MQ 统一复用 | TC-02 | 是 | 代码入口复用 `AbstractMQ`、`MQMsgReceiver` |
| 一期首批 key 路由规则 | TC-03 | 是 | 通过代码审查与单测覆盖 |
| 同步删除失败阻断主请求 | TC-E04 | 部分 | 逻辑已落地，真实 Redis 失败路径待联调 |
| 击穿、穿透、雪崩保护 | TC-04、TC-E03 | 是 | 代码级已落地到 `user` 读链路 |

## 3. 测试前提与环境准备

### 3.1 环境信息

| 项目 | 内容 |
| :--- | :--- |
| 分支 | `refactor/cache-consistency-cdc` |
| 部署环境 | 本地开发 / Codex 沙箱 |
| 服务状态 | 未完整启动 Java 服务，主要执行静态检查、Maven 测试与文档校验 |
| 外部依赖 | Redis、Kafka、MySQL(H2 测试替身部分可用) |
| 相关配置 | `tolink.cache-consistency.sync-delete-required=false` 仅在 `link-api` 测试环境配置中启用 |

### 3.2 测试前提

- 本期代码已提交到当前分支
- Redis / MQ 公共契约已同步更新
- 旧 `DoubleDeleteCacheService` 已删除
- 当前环境无法稳定提供真实 Redis / Kafka 服务

### 3.3 测试数据准备

| 数据项 | 用途 | 准备方式 | 备注 |
| :--- | :--- | :--- | :--- |
| 用户缓存 key | 验证 `user` 域缓存读保护与联合删除 | 代码路径检查 + 测试桩 | `user:info:*` / `user:role:*` |
| 厂商配置 key | 验证 `llm:pvd:{providerType}` 驱逐口径统一 | 单测 + 代码检查 | 已从按 `id` 删除改为按 `providerType` |
| 用户 LLM 配置 key | 验证 `llm:cfg:*` / `llm:u_def:*` 联动驱逐 | 代码检查 | 新增写路径联动删除 |
| 缓存补偿消息体 | 验证 `tolink.cache.evict` 扁平 JSON 契约 | 单元测试 | 使用 `event_id/cache_target/route_id/...` |

### 3.4 执行方式

- 单元测试
- Maven 模块测试
- 代码审查
- 文档一致性检查
- 配置检查

## 4. 测试执行清单

### 4.1 主流程测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-01 | 业务写路径切换到统一同步删缓存执行器 | 代码已完成改造 | 1. 检查 `UserCacheServiceImpl`、`AdminProviderServiceImpl`、`UserLLMConfigServiceImpl` 2. 确认不再依赖 `DoubleDeleteCacheService` 3. 确认改为调用 `CacheConsistencyService` | 首批业务写路径统一接入新执行器 | 已确认首批写路径全部切换，旧双删类已删除 | 通过 | Codex |
| TC-02 | 缓存补偿消息模型与 Kafka 接收器落地 | MQ 模型与接收器代码已新增 | 1. 检查 `CacheCompensationMQ` 2. 检查 `CacheCompensationKafkaReceiver` 3. 核对 topic / group 与消息字段 | 存在统一扁平 MQ 契约并接入 Kafka 消费入口 | 已落地 `tolink.cache.evict` 与 `tolink-cache-evict`，消息体字段完整 | 通过 | Codex |
| TC-03 | 首批缓存 key 路由规则落地 | Redis framework 已新增路由器 | 1. 检查 `CacheEvictTarget` 2. 检查 `CacheKeyRouter` 3. 确认 `USER` 联合删除 | 一期首批 key 均有统一路由，`user` 域支持联合删除 | 已确认 `user:info`、`user:role`、`llm:cfg`、`llm:u_def`、`llm:pvd` 路由存在 | 通过 | Codex |
| TC-04 | `user` 读链路接入缓存读保护 | 读保护代码已新增 | 1. 检查 `CacheReadProtectionService` 2. 检查 `UserCacheService#getOrLoad` 3. 检查 `AuthServiceImpl` / `StpInterfaceImpl` 调用 | `user` 读链路已接入空值缓存、回源合并、TTL 抖动 | 已确认接入完成 | 通过 | Codex |

### 4.2 异常与边界测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-E01 | 缓存补偿消息缺少必填字段 | 构造非法消息体 | 1. 检查 `CacheCompensationMQ.validate` 2. 核对缺字段异常口径 | 非法消息被拒绝，不进入缓存删除 | 已通过代码检查确认校验逻辑存在 | 通过 | Codex |
| TC-E02 | provider 缓存驱逐口径错误回归 | `AdminProviderServiceImpl` 已改造 | 1. 检查更新/删除/启停逻辑 2. 确认按 `providerType` 驱逐 | 不再出现按 `id` 删除 `llm:pvd:*` | 已确认修复 | 通过 | Codex |
| TC-E03 | 缓存击穿/穿透/雪崩保护边界 | 读保护代码已存在 | 1. 检查 `NULL_MARKER` 2. 检查 `ReentrantLock` 回源合并 3. 检查 TTL 抖动逻辑 | 三类基础防护都存在统一实现 | 已通过代码检查确认 | 通过 | Codex |
| TC-E04 | 同步删缓存失败路径 | 需要真实 Redis 不可用或删除异常场景 | 1. 检查 `CacheConsistencyService` 2. 核对 `sync-delete-required` 开关 3. 核对 `CACHE_DELETE_FAILED` 错误码 | 生产默认失败，测试环境可配置放行 | 已通过代码检查确认逻辑；真实 Redis 场景未完成联调 | 阻塞 | Codex |

### 4.3 回归检查项

| 检查项 | 检查方式 | 预期结果 | 实际结果 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| 关联旧功能是否受影响 | 检查 `user`、`provider`、`llm-config` 入口改造 | 业务入口仍保持原有对外接口 | 代码层确认接口未改动 | 通过 |
| 关键接口兼容性 | 审查 Controller 与 Service 签名 | 对外 API 不新增、不变更参数 | 已确认 | 通过 |
| 关键数据读写正确性 | 检查 key 路由、写路径驱逐、读路径回填 | 缓存 key 口径统一 | 已确认 | 通过 |
| 真实 Redis/Kafka 集成验证 | 本地联调 | 依赖真实中间件时可完成联通性验证 | 当前环境受限未完成 | 阻塞 |

## 5. 执行证据记录

### 5.1 接口与页面结果

- 本次未执行完整前端或 API 联调，仅完成代码级与测试命令级验证。

### 5.2 日志与链路记录

- `mvn test` 过程中已观察到：
  - `CacheConsistencyService` 在无 Redis 环境下会按预算重试，并在测试环境配置允许时放行。
  - Kafka listener 在无 broker 环境下会出现连接告警，说明当前全量 `link-api` 测试依赖外部 Kafka。

### 5.3 数据库 / 缓存 / MQ / OSS 校验结果

- Redis：代码契约与配置已校验，未完成真实 Redis 验证。
- MQ：消息模型、topic、group 与消费者代码已校验，未完成真实 Kafka broker 验证。
- MySQL：本期未改表，主数据仍复用原有结构。

## 6. 问题记录与处理结果

| 编号 | 问题现象 | 影响范围 | 严重级别 | 当前状态 | 处理结果 / 临时结论 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-01 | 全量 `link-api` 测试依赖本地 Redis | API 集成测试 | 中 | 未修复 | 当前通过测试配置降低部分影响，但仍需后续引入 Redis 替身或测试容器 |
| BUG-02 | 全量 `link-api` 测试会启动 Kafka listener 并直连本地 broker | API 集成测试 | 中 | 未修复 | 当前环境无法稳定验证，需后续补充 Kafka 测试隔离方案 |

## 7. 交付结论

### 7.1 测试结论

- 主流程是否通过：代码级通过
- 异常流程是否通过：代码级大部分通过，真实 Redis/Kafka 依赖场景未完整验证
- 回归检查是否通过：静态回归通过，完整集成回归待补

### 7.2 遗留风险

- 当前无法给出“真实 Redis + Kafka + Canal 全链路已验证完成”的结论。
- 一期只把 `user` 域读链路接上了读保护，其他业务域仍待二期接入。

### 7.3 是否允许交付

- 是否可交付：有条件可交付
- 交付前提：
  - 在可用 Redis / Kafka 环境补跑一次集成联调
  - 验证 `tolink.cache.evict` 主题的真实消费链路
- 联调注意事项：
  - 生产环境应保持 `tolink.cache-consistency.sync-delete-required=true`
  - 测试环境如无 Redis，可临时关闭强校验，但不能带入正式环境
- 发布 / 回滚注意事项：
  - 发布前需确认 Redis、Kafka、Canal 配置齐全
  - 如补偿链路异常，可先停用新 topic 消费并保留同步删缓存主链路

## 8. 回写检查

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填测试结论 | 是 | 已更新当前状态与测试结论 |
| `project_info.md` 已同步更新 | 是 | 已更新 Redis / MQ 能力现状 |
| 本次遗留风险已显式记录 | 是 | 已写入第 6、7 节 |
| 交付结论已明确 | 是 | 当前结论为“有条件可交付” |
