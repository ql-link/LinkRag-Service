# ToLink Service 缓存一致性改造一期测试执行与交付记录

> **文档状态：** 已完成，待最终审核确认
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

## 0. 文档修订记录 (Change Log)

| 版本号 | 修改日期 | 修改内容简述 | 修改人 | 审核人 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-06 | 初始化一期测试执行与交付记录，沉淀测试范围、风险与交付结论 | Codex | Fang |
| v1.1 | 2026-05-06 | 补充最新定向单测结果，统一交付状态为待最终审核确认 | Codex | Fang |
| v1.2 | 2026-05-06 | 补充真实 Redis/Kafka 联调结果，并记录联调中发现的 LLM 配置字段映射缺陷及修复 | Codex | Fang |

## 1. 使用说明

本文件用于指导一期缓存一致性改造的验证与交付判断。

当前状态说明：

- 已完成代码实现与文档回填
- 已完成一轮命令级验证、局部单测验证与真实 Redis/Kafka 联调
- 已补跑缓存一致性模块定向单测，`19` 个用例全部通过
- 已完成主写链路与补偿删缓存链路的真实环境验证
- 仍未完成 Redis 故障注入场景与 Canal 真实产流场景验证

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
| 写库成功后同步删除缓存 | TC-01、TC-E04、IT-03、IT-04、IT-05 | 是 | `user` 读写链路已验证，`provider` / `llm-config` 当前仅验证写链路同步删缓存 |
| CDC 异步二次删除补偿 | TC-02、IT-06、IT-07 | 是 | 已通过 Kafka 合法消息联调验证 |
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
| 服务状态 | 已启动真实 `link-api` 实例，并完成本地联调 |
| 外部依赖 | Redis、Kafka、MySQL(H2 测试替身部分可用) |
| 相关配置 | `tolink.cache-consistency.sync-delete-required=false` 仅在 `link-api` 测试环境配置中启用 |

### 3.2 测试前提

- 本期代码已提交到当前分支
- Redis / MQ 公共契约已同步更新
- 旧 `DoubleDeleteCacheService` 已删除
- 当前环境可连接远端 MySQL / Redis / Kafka，且已完成一次联调

### 3.3 测试数据准备

| 数据项 | 用途 | 准备方式 | 备注 |
| :--- | :--- | :--- | :--- |
| 用户缓存 key | 验证 `user` 域缓存读保护与联合删除 | 代码路径检查 + 测试桩 | `user:info:*` / `user:role:*` |
| 厂商配置 key | 验证 `llm:pvd:{providerType}` 驱逐口径统一 | 单测 + 代码检查 | 已从按 `id` 删除改为按 `providerType` |
| 用户 LLM 配置 key | 验证 `llm:cfg:*` / `llm:u_def:*` 联动驱逐 | 真实 Redis 预置 + 接口联调 | 联调期间同时修复 `capability` 列映射缺陷 |
| 缓存补偿消息体 | 验证 `tolink.cache.evict` 扁平 JSON 契约 | 单元测试 | 使用 `event_id/cache_target/route_id/...` |

### 3.4 执行方式

- 单元测试
- Maven 模块测试
- 代码审查
- 文档一致性检查
- 配置检查

### 3.5 联调执行清单（执行结果）

| 编号 | 联调项 | 执行步骤 | 预期结果 | 失败判定 |
| :--- | :--- | :--- | :--- | :--- |
| IT-01 | 环境配置核对 | 已确认 `application-local.yml` 指向远端 `36.213.180.176` 的 MySQL / Redis / Kafka；运行实例成功连通三者 | 生产口径配置可启动缓存一致性能力 | 通过 |
| IT-02 | Kafka 消费者就绪校验 | 已确认启动日志出现 `tolink.cache.evict` 订阅与 `tolink-cache-evict` 消费组加入成功 | Kafka listener 已正常启动 | 通过 |
| IT-03 | 同步删缓存主链路验证-用户资料 | 预置 `user:info:10008`、`user:role:10008` 后调用 `PATCH /api/v1/user/profile`，Redis `EXISTS` 返回 `0` | 用户资料写链路同步删缓存生效 | 通过 |
| IT-04 | 同步删缓存主链路验证-厂商配置 | 创建 provider `openai_it_20260506`，预置 `llm:pvd:openai_it_20260506` 后调用 `PATCH /api/v1/admin/providers/10151`，Redis `EXISTS` 返回 `0` | provider 写链路按 `providerType` 删除缓存 | 通过 |
| IT-05 | 同步删缓存主链路验证-用户 LLM 配置 | 创建配置 `id=10191`，预置 `llm:cfg:10191`、`llm:u_def:10008` 后调用 `PATCH /api/v1/llm/configs/10191`，Redis `EXISTS` 返回 `0` | 用户 LLM 配置写链路联动删两把 key | 通过 |
| IT-06 | CDC 到 MQ 补偿链路验证 | 使用 Kafka CLI 向 `tolink.cache.evict` 发送合法消息 `evt-it06-001` | Java 消费端成功消费消息 | 通过 |
| IT-07 | 补偿删除结果验证 | 预置 `user:info:10008`、`user:role:10008` 后发送合法补偿消息，Redis `EXISTS` 返回 `0` | 补偿链路可收敛脏缓存 | 通过 |
| IT-08 | 非法消息防御验证 | 发送缺失 `route_id` 的非法消息，Redis 中预置 key 保持存在 | 非法消息未触发误删缓存 | 通过（日志细节由人工侧继续确认） |
| IT-09 | Redis 故障下同步删失败策略 | 尚未执行断链或异常注入 | 待验证 `CACHE_DELETE_FAILED` 失败口径 | 阻塞 |
| IT-10 | 补偿链路可观测性验证 | 已看到成功链路日志；非法消息拒绝日志建议在人工观察端补截图留证 | 成功和失败日志都应可追踪 | 部分通过 |

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
| TC-E04 | 同步删缓存失败路径 | 需要真实 Redis 不可用或删除异常场景 | 1. 检查 `CacheConsistencyService` 2. 核对 `sync-delete-required` 开关 3. 核对 `CACHE_DELETE_FAILED` 错误码 | 生产默认失败，测试环境可配置放行 | 已通过代码检查确认逻辑；真实 Redis 断链注入仍未执行 | 阻塞 | Codex |

### 4.3 回归检查项

| 检查项 | 检查方式 | 预期结果 | 实际结果 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| 关联旧功能是否受影响 | 检查 `user`、`provider`、`llm-config` 入口改造 | 业务入口仍保持原有对外接口 | 代码层确认接口未改动 | 通过 |
| 关键接口兼容性 | 审查 Controller 与 Service 签名 | 对外 API 不新增、不变更参数 | 已确认 | 通过 |
| 关键数据读写正确性 | 检查 key 路由、写路径驱逐、读路径回填 | 缓存 key 口径统一 | 已确认 | 通过 |
| 真实 Redis/Kafka 集成验证 | 本地联调 | 依赖真实中间件时可完成联通性验证 | 已完成主写链路与补偿链路联调；故障注入仍缺 | 部分通过 |

## 5. 执行证据记录

### 5.1 接口与页面结果

- `POST /api/v1/auth/register` 成功创建联调用户 `10008`
- `PATCH /api/v1/user/profile` 成功，验证 `user:info:10008` / `user:role:10008` 同步删除
- `POST /api/v1/admin/providers` 成功创建联调 provider `openai_it_20260506`
- `PATCH /api/v1/admin/providers/10151` 成功，验证 `llm:pvd:openai_it_20260506` 写链路同步删除
- `POST /api/v1/llm/configs` 成功创建联调配置 `10191`
- `PATCH /api/v1/llm/configs/10191` 成功，验证 `llm:cfg:10191` / `llm:u_def:10008` 写链路联动删除

### 5.2 日志与链路记录

- 已执行定向验证命令：
  - `mvn -pl link-service -am -Dtest=UserCacheServiceImplTest,AdminProviderServiceImplTest,UserLLMConfigServiceImplTest,CacheCompensationMQTest,CacheCompensationKafkaReceiverTest test`
- 已执行真实联调操作：
  - 通过 `redis-cli` 预置并检查 `user:*`、`llm:pvd:*`、`llm:cfg:*`、`llm:u_def:*`
  - 通过 `kafka-console-producer` 向 `tolink.cache.evict` 发送合法与非法消息
- 最近一次定向验证结果：
  - `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`
- `mvn test` 过程中已观察到：
  - `CacheConsistencyService` 在无 Redis 环境下会按预算重试，并在测试环境配置允许时放行。
  - Kafka listener 在无 broker 环境下会出现连接告警，说明当前全量 `link-api` 测试依赖外部 Kafka。
- 真实联调期间额外发现：
  - `llm_user_config` 实际列名为 `capability`，实体原映射缺失导致 `GET /api/v1/llm/configs`、更新、删除出现 `Unknown column 'capabilities'`
  - 已在 `UserLLMConfig` 中补 `@TableField("capability")` 并重新安装 `link-model` 到本地 Maven 仓库后验证恢复

### 5.3 数据库 / 缓存 / MQ / OSS 校验结果

- Redis：已完成真实 key 预置、同步删除与补偿删除验证；故障注入未完成。
- MQ：已完成真实 Kafka broker 消费验证；合法与非法消息各执行一轮。
- MySQL：本期未改表，主数据仍复用原有结构。

## 6. 问题记录与处理结果

| 编号 | 问题现象 | 影响范围 | 严重级别 | 当前状态 | 处理结果 / 临时结论 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-01 | 全量 `link-api` 测试依赖本地 Redis | API 集成测试 | 中 | 未修复 | 当前通过测试配置降低部分影响，但仍需后续引入 Redis 替身或测试容器 |
| BUG-02 | 全量 `link-api` 测试会启动 Kafka listener 并直连本地 broker | API 集成测试 | 中 | 未修复 | 当前环境无法稳定验证，需后续补充 Kafka 测试隔离方案 |
| BUG-03 | `llm_user_config.capability` 字段未映射到实体 `capabilities` | LLM 配置查询、更新、删除接口 | 高 | 已修复 | 已在 `UserLLMConfig` 补充 `@TableField("capability")`，并通过 `8081` 实例复验配置查询与联动删缓存恢复 |

## 7. 交付结论

### 7.1 测试结论

- 主流程是否通过：通过；`user` 读写链路与 `provider` / `llm-config` 写链路已完成真实联调
- 异常流程是否通过：非法 MQ 消息防御已验证，Redis 故障注入场景仍待补
- 回归检查是否通过：主要集成链路通过，故障注入与 Canal 真产流待补

### 7.2 遗留风险

- 当前无法给出“真实 Redis + Kafka + Canal 全链路已验证完成”的结论。
- 一期只把 `user` 域读链路接上了读保护，`provider` / `llm-config` 读链路仍待二期接入。
- `IT-10` 的非法消息错误日志建议由人工侧再补一张日志截图，作为最终审核佐证。

### 7.3 是否允许交付

- 是否可交付：有条件可交付
- 交付前提：
  - 补跑 `IT-09` Redis 故障注入场景
  - 补齐 `IT-10` 非法消息日志留证，最好同时确认 `event_id / cache_target / route_id` 字段输出
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
