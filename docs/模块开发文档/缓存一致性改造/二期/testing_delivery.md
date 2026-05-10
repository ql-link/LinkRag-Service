# ToLink Service 缓存一致性改造二期测试执行与交付记录

> **文档状态：** 已完成，待远端部署联调
> **项目名称**：ToLink Service
> **模块名称**：缓存一致性改造
> **当前期次**：二期
> **需求文档**：[requirement.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/二期/requirement.md)
> **技术文档**：[technical_design.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/二期/technical_design.md)
> **改造报告**：[implementation_report.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/二期/implementation_report.md)
> **部署文档**：[canal_deployment.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/缓存一致性改造/二期/canal_deployment.md)
> **分支名称**：refactor/cache-consistency-cdc
> **执行人：** Codex
> **最后更新时间：** 2026-05-06

---

## 1. 使用说明

本文件用于记录缓存一致性改造二期的 Java 侧验证结果、远端结构核对结果和交付判断。

本期结论口径：

- Java 侧实现与文档交付可以认为完成。
- 生产级双写一致性闭环仍需 Canal / CDC 转换桥真实部署和远端联调验证。

---

## 2. 测试范围与目标

### 2.1 本次要验证的内容

- `llm-config` 配置详情缓存和默认配置映射是否接入共享读保护能力。
- 写路径是否继续删除 `llm:cfg:{configId}` 和 `llm:u_def:{userId}`。
- Kafka 缓存补偿消费者和消息契约是否保持可用。
- LLM 能力配置改造后，API 层回归测试是否通过。
- 远端 MySQL 关键表结构是否支持当前代码发布。

### 2.2 本次不验证的内容

- 不执行真实 Canal Server 部署。
- 不执行 CDC 转换桥生产部署。
- 不执行真实 `MySQL binlog -> Canal -> Kafka -> Java -> Redis` 全链路联调。
- 不对历史文件解析表做迁移验证。

### 2.3 验收项映射

| 验收项 | 对应用例编号 | 是否覆盖 | 备注 |
| :--- | :--- | :--- | :--- |
| 共享读治理能力复用 | TC-01 | 是 | `UserLLMConfigCacheService` 复用 `CacheReadProtectionService` |
| `llm-config` 读链路接入 | TC-02 | 是 | 配置详情与默认映射已接入 |
| 写侧补偿一致性 | TC-03 | 是 | 写路径同步删缓存，CDC 消费者沿用一期 |
| Canal 文档交付 | TC-04 | 是 | 已新增 `canal_deployment.md` |
| 远端表结构核对 | TC-05 | 是 | 已识别 `llm_user_config` 索引差异 |

---

## 3. 测试前提与环境准备

### 3.1 环境信息

| 项目 | 内容 |
| :--- | :--- |
| 分支 | `refactor/cache-consistency-cdc` |
| 部署环境 | 本地开发环境 + 远端 MySQL 只读核对 |
| 服务状态 | 未启动真实业务服务，使用 Maven 自动化测试 |
| 外部依赖 | H2 测试库；远端 MySQL `36.213.180.176:3306/tolink_rag_db` |
| 相关配置 | Kafka 测试环境日志中 `127.0.0.1:19092` broker 不可用 warning 不阻塞测试结果 |

### 3.2 测试前提

- 一期缓存一致性框架已在当前分支存在。
- `LLM能力与默认配置/一期` 已完成 Java 侧实现和测试交付。
- `middleware_contract.md` 已记录 LLM 配置缓存 key、TTL 和一致性策略。
- 远端 MySQL 允许执行 `SHOW CREATE TABLE` 和 information_schema 查询。

### 3.3 测试数据准备

| 数据项 | 用途 | 准备方式 | 备注 |
| :--- | :--- | :--- | :--- |
| H2 `llm_user_config` | API / Service 自动化测试 | `link-api/src/test/resources/schema.sql` | 已包含 `capability` 字段 |
| H2 `llm_system_provider` | LLM 配置创建测试 | `ConfigControllerTest` 插入 | 兼容旧数组模型格式 |
| 远端 `llm_user_config` | 结构差异核对 | `SHOW CREATE TABLE` | 仅查询，不改库 |
| 远端 `llm_system_provider` | `supported_models` 数据形态核对 | `SELECT JSON_PRETTY(supported_models)` | 发现旧数组格式 |

### 3.4 执行方式

- 单元测试：已执行。
- 集成测试：已执行。
- 数据库检查：已执行远端只读查询。
- Redis / MQ / Canal 检查：仅完成 Java 消费端与文档层检查，未做远端部署联调。

---

## 4. 测试执行清单

### 4.1 主流程测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-01 | 共享读保护复用 | `CacheReadProtectionService` 存在 | 检查 `UserLLMConfigCacheServiceImpl` 读取实现 | 单对象缓存读取复用空值缓存、回源合并、TTL 抖动 | 已接入 `getOrLoad` | 通过 | Codex |
| TC-02 | LLM 默认配置缓存读取 | 用户存在默认配置 | 执行 API / Service 回归测试 | 默认配置按能力读取，缓存 miss 可回源 | `link-api` 测试通过 | 通过 | Codex |
| TC-03 | 写路径缓存失效 | 创建、更新、删除、设置默认 | 检查 Service 写路径和测试 | 写库成功后删除配置详情和默认映射缓存 | 已调用 owner service 驱逐 | 通过 | Codex |
| TC-04 | Canal 部署文档 | 需要部署说明 | 检查 `canal_deployment.md` | 覆盖部署前提、消息契约、验证和回滚 | 文档已补齐 | 通过 | Codex |
| TC-05 | 远端表结构核对 | 可连接远端 MySQL | 执行 `SHOW CREATE TABLE` | 明确发布前差异 | 已识别索引和历史表差异 | 通过 | Codex |

### 4.2 异常与边界测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-E01 | 缓存 mock 返回空导致用户接口无 data | 用户接口测试 mock 了 `UserCacheService` | 执行 `UserControllerTest` | mock 应执行 loader 回源，接口返回完整 data | 已提交 `test(user)` 修复，测试通过 | 通过 | Codex |
| TC-E02 | 远端旧唯一索引阻塞能力拆行 | 远端仍为 `uk_user_provider_model` | 核对远端索引 | 发布前必须迁移为带 `capability` 唯一索引 | 已记录为阻塞发布风险 | 有条件通过 | Codex |
| TC-E03 | Canal 原始消息直接投递 Java | 未部署 CDC 转换桥 | 检查部署文档 | 文档必须说明 Java 只接收扁平 JSON | 已明确必须有 CDC 转换桥 | 通过 | Codex |

### 4.3 回归检查项

| 检查项 | 检查方式 | 预期结果 | 实际结果 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| API 回归 | `mvn -pl link-api -am test` | 所有 API 测试通过 | 58 tests，0 failures，0 errors | 通过 |
| LLM 配置创建返回 | `ConfigControllerTest` | 创建接口按数组返回能力配置 | 测试通过 | 通过 |
| 用户资料接口 | `UserControllerTest` | 缓存 mock 下仍能返回 `data.username` / `data.nickname` | 测试通过 | 通过 |
| 远端 LLM 表结构 | `SHOW CREATE TABLE` | 明确是否可直接发布 | 发现索引需迁移 | 有条件通过 |

---

## 5. 执行证据记录

### 5.1 命令结果

已执行：

```bash
mvn -pl link-api -am test
```

结果：

```text
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 5.2 远端数据库核对

已执行远端只读核对：

```sql
SHOW CREATE TABLE llm_user_config;
SHOW CREATE TABLE llm_system_provider;
SHOW CREATE TABLE sys_user;
```

关键发现：

- `llm_user_config.capability` 已存在。
- 远端唯一索引仍为 `uk_user_provider_model(user_id, provider_id, model_name)`。
- 当前代码期望唯一索引为 `uk_user_provider_model_capability(user_id, provider_id, model_name, capability)`。
- 远端 `llm_system_provider.supported_models` 仍存在旧数组数据，例如 `["gpt-4"]`。

### 5.3 Redis / MQ / Canal 校验结果

- Redis：Java 侧 key 约定已写入 `middleware_contract.md`。
- MQ：Java 侧 topic 仍为 `tolink.cache.evict`，consumer group 默认 `tolink-cache-evict`。
- Canal：未部署验证，已在 `canal_deployment.md` 中给出部署和验收清单。

---

## 6. 问题记录与处理结果

| 编号 | 问题现象 | 影响范围 | 严重级别 | 当前状态 | 处理结果 / 临时结论 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-01 | `UserControllerTest` 缓存 mock 返回 null 导致 JSON path 缺失 | API 回归测试 | 中 | 已修复 | 新增 mock answer 执行 loader 回源 |
| RISK-01 | 远端 `llm_user_config` 唯一索引未带 `capability` | LLM 能力拆行发布 | 高 | 待发布处理 | 发布前必须迁移索引 |
| RISK-02 | 远端 `supported_models` 数据仍为旧数组 | 按能力查询准确性 | 中 | 可兼容 | 当前代码兼容为 `CHAT`，多能力需后续迁移数据 |
| RISK-03 | Canal / CDC 转换桥未部署 | 生产级二次删除补偿 | 高 | 待联调 | 文档已补齐，真实部署后再验收 |

---

## 7. 交付结论

### 7.1 测试结论

- 主流程是否通过：通过，Java 侧 API 回归和 LLM 配置缓存接入已验证。
- 异常流程是否通过：有条件通过，远端表结构迁移和 Canal 部署仍需后续执行。
- 回归检查是否通过：通过，`mvn -pl link-api -am test` 成功。

### 7.2 遗留风险

- 远端 `llm_user_config` 索引未迁移前，不建议直接发布同模型多能力拆行功能。
- Canal / CDC 转换桥未部署前，生产级双写一致性闭环不能宣称完成。
- 远端历史文件解析表与当前脚本不一致，不属于本期阻塞项，但会影响整体环境对齐。

### 7.3 是否允许交付

- 是否可交付：有条件可交付。
- 交付前提：Java 侧代码和二期文档可合并；远端发布前需完成 `llm_user_config` 索引迁移。
- 联调注意事项：按 `canal_deployment.md` 验证 Kafka 直投、默认配置映射删除、系统厂商缓存删除。
- 发布 / 回滚注意事项：发布前清理旧 `llm:u_def:{userId}`；回滚时删除新语义默认映射缓存，避免旧代码错读。

---

## 8. 回写检查

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填测试结论 | 是 | 状态已更新 |
| `project_info.md` 已同步更新 | 是 | LLM 能力配置与缓存现状已记录 |
| 本次遗留风险已显式记录 | 是 | 见 RISK-01 / RISK-02 / RISK-03 |
| 交付结论已明确 | 是 | Java 侧有条件可交付 |
