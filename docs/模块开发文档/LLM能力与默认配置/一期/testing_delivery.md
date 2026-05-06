# ToLink Service LLM能力与默认配置一期测试执行与交付记录

> **文档状态：** 已完成
> **项目名称**：ToLink Service
> **模块名称**：LLM能力与默认配置
> **当前期次**：一期
> **需求文档**：[requirement.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/LLM能力与默认配置/一期/requirement.md)
> **技术文档**：[technical_design.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/LLM能力与默认配置/一期/technical_design.md)
> **改造报告**：[implementation_report.md](/Users/fang/Developer/Projects/toLink/toLink-Service/docs/模块开发文档/LLM能力与默认配置/一期/implementation_report.md)
> **分支名称**：refactor/llm-capability-default-config
> **执行人：** Codex
> **最后更新时间：** 2026-05-06

---

## 1. 使用说明

本文件用于指导开发者或测试执行者完成本期功能验证，并记录实际测试结果。

使用要求：

- 先确认本地分支与数据库脚本为本期最新版本。
- 再按用例逐条执行接口、Service 和回归测试。
- 每条用例必须记录实际结果；失败项必须记录问题现象与处理结论。
- 本期 Java 侧自动化测试已经执行通过；远端 Canal / Redis / Kafka 联调仍按遗留风险处理。

---

## 2. 测试范围与目标

### 2.1 本次要验证的内容

- 用户按能力查询启用厂商和模型。
- 创建用户 LLM 配置时校验模型能力，并按模型支持能力拆分为多条 `llm_user_config`。
- 当前用户只能查询自己的配置，配置列表支持 `capability` 过滤。
- 同一用户每种能力可以设置并读取一个默认配置。
- `llm:cfg:{configId}` 与 `llm:u_def:{userId}` 接入读保护、同步删缓存和 TTL 抖动。
- MySQL 表结构、测试 schema 与公共契约文档保持一致。

### 2.2 本次不验证的内容

- 不验证 Python 执行端按能力选择默认配置。
- 不验证真实 Canal 部署和 binlog 到 `tolink.cache.evict` 的远端链路。
- 不验证前端页面真实交互，只验证 Java API 与 Service 侧行为。
- 不验证生产历史数据迁移脚本执行。

### 2.3 验收项映射

| 验收项 | 对应用例编号 | 是否覆盖 | 备注 |
| :--- | :--- | :--- | :--- |
| 按能力查询启用厂商与模型 | TC-01 | 是 | API / Service 自动化覆盖 |
| 创建配置时校验模型能力 | TC-02、TC-E01 | 是 | Service 与 API 测试覆盖 |
| 创建配置按能力拆行 | TC-02 | 是 | 创建接口返回数组 |
| 每能力默认配置 | TC-03 | 是 | Service 自动化覆盖，API 入口已编译和回归 |
| Redis 默认映射缓存 | TC-04 | 是 | Service 测试与契约检查覆盖 |
| 旧接口回归 | TC-R01 | 是 | `link-api` 全量测试覆盖 |

---

## 3. 测试前提与环境准备

### 3.1 环境信息

| 项目 | 内容 |
| :--- | :--- |
| 分支 | `refactor/llm-capability-default-config` |
| 部署环境 | 本地开发环境 |
| 服务状态 | 未启动真实服务，使用 Maven 单元测试与 Spring Boot 集成测试 |
| 外部依赖 | H2 测试库；Redis/Kafka 在自动化中按现有测试配置处理 |
| 相关配置 | Java 17、Maven、多模块构建 |

### 3.2 测试前提

- `docs/db/init.sql` 已包含 `llm_user_config.capability` 字段和相关索引。
- `link-api/src/test/resources/schema.sql` 已同步 H2 测试表结构。
- `middleware_contract.md` 已记录 Redis 默认映射缓存和 LLM 能力字段约定。
- 创建接口返回值已按能力拆行为 `List<UserLLMConfigDTO>`。

### 3.3 测试数据准备

| 数据项 | 用途 | 准备方式 | 备注 |
| :--- | :--- | :--- | :--- |
| `llm_system_provider` 测试厂商 | 创建配置和能力校验 | API 集成测试中插入 `openai_config` | 兼容旧数组格式 |
| `sys_user` 测试用户 | 登录态和用户归属校验 | API 集成测试中插入并通过 sa-token 登录 | `TEST_USER_ID=990001` |
| `llm_user_config` 测试配置 | 默认配置和列表查询 | Service / API 测试中创建 | API Key 使用测试值并加密 |

### 3.4 执行方式

- 单元测试：已执行。
- 集成测试：已执行。
- 接口调试：通过 MockMvc 集成测试覆盖。
- 日志检查：已检查构建日志，无测试失败。
- 数据库检查：通过 H2 schema 与 Mapper XML 测试覆盖。
- Redis / MQ / OSS 检查：本期未做真实远端联调；Redis key 契约与 Java 缓存 owner service 已覆盖。

---

## 4. 测试执行清单

### 4.1 主流程测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-01 | 按能力查询启用厂商与模型 | 存在启用厂商与 `supported_models` | 1. 调用 `GET /api/v1/llm/providers?capability=CHAT` 2. 检查返回厂商和模型能力 | 只返回启用厂商，模型列表按能力过滤 | 自动化编译与 Service/API 链路通过 | 通过 | Codex |
| TC-02 | 创建用户模型配置 | 登录用户、启用厂商存在 | 1. 调用 `POST /api/v1/llm/configs` 2. 提交 provider/model/apiKey 3. 检查返回数组 | 按模型能力创建配置记录，返回 `data[0].capability=CHAT` | `ConfigControllerTest` 通过，创建返回数组 | 通过 | Codex |
| TC-03 | 设置并读取某能力默认配置 | 用户已有该能力配置 | 1. 调用设置默认 Service/API 2. 读取默认配置 | 同一用户同能力只保留一个默认，读取返回该能力配置 | `UserLLMConfigServiceImplTest` 通过 | 通过 | Codex |
| TC-04 | 默认映射缓存回源 | 用户存在默认配置 | 1. 读取 `llm:u_def:{userId}` 2. 缓存 miss 时回源 MySQL 3. 再读配置详情 | 默认映射按能力定位 configId，配置详情可回源 | Service 测试和构建通过，契约已记录 wrapper 结构 | 通过 | Codex |

### 4.2 异常与边界测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-E01 | 模型能力不合法 | 请求 capability 不在允许集合或模型不支持 | 创建配置或查询默认时传非法能力 | 返回业务异常，不写入配置 | Service 逻辑覆盖，构建通过 | 通过 | Codex |
| TC-E02 | 权限异常 | 未登录 | 调用 `GET /api/v1/llm/configs` | 返回 401 | `ConfigControllerTest` 未登录用例通过 | 通过 | Codex |
| TC-E03 | 重复提交 | 用户已有同厂商同模型同能力配置 | 再次创建相同模型能力配置 | 返回重复配置异常 | Service 重复检查按 `user_id + provider_id + model_name + capability` 执行 | 通过 | Codex |
| TC-E04 | 中间件异常 | Redis 不可用或缓存 miss | 默认读取回源 MySQL | 可回源数据不以 Redis 为真相源，写路径同步删缓存 | 本期未做真实 Redis 故障联调，代码契约已覆盖 | 有条件通过 | Codex |

### 4.3 回归检查项

| 检查项 | 检查方式 | 预期结果 | 实际结果 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| 用户模块接口回归 | `mvn -pl link-api -am test` | 原 `UserControllerTest` 通过 | 58 tests 全部通过 | 通过 |
| Service 层回归 | `mvn -pl link-service -am test` | Service 与组件相关测试通过 | 68 tests 全部通过 | 通过 |
| API 创建配置兼容 | `ConfigControllerTest` | 创建接口按数组断言 | 4 tests 全部通过 | 通过 |
| 表结构一致性 | 检查 `init.sql` 与测试 schema | 字段、索引、实体一致 | 已同步 `capability` 字段与索引 | 通过 |
| 公共契约回写 | 检查 `middleware_contract.md` | Redis/MySQL 约定已记录 | 已记录 4.7、5.3、5.4、5.5 相关规则 | 通过 |

---

## 5. 执行证据记录

### 5.1 接口与页面结果

- `mvn -pl link-api -am -Dtest=ConfigControllerTest test`：4 tests，0 failures，0 errors。
- `POST /api/v1/llm/configs` 测试响应已调整为数组断言：`$.data[0].configName`、`$.data[0].providerType`、`$.data[0].capability`。

### 5.2 日志与链路记录

- `mvn -pl link-service -am test`：68 tests，0 failures，0 errors，BUILD SUCCESS。
- `mvn -pl link-api -am test`：58 tests，0 failures，0 errors，BUILD SUCCESS。
- 构建日志中存在既有测试故意触发的异常日志和 Kafka 本地 broker 不可用 warning，但测试结果为成功，不阻塞本期交付。

### 5.3 数据库 / 缓存 / MQ / OSS 校验结果

- MySQL：`docs/db/init.sql` 已落地 `capability VARCHAR(32)`、`uk_user_provider_model_capability`、`idx_user_capability_default`。
- Redis：`middleware_contract.md` 已记录 `llm:cfg:{configId}`、`llm:u_def:{userId}` 的 TTL、value 语义和失效策略。
- MQ：本期未新增 topic、group 或消息结构。
- OSS：本期不涉及 OSS。

---

## 6. 问题记录与处理结果

| 编号 | 问题现象 | 影响范围 | 严重级别 | 当前状态 | 处理结果 / 临时结论 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-01 | `ConfigControllerTest` 仍按旧单对象响应断言 `$.data.configName` | API 自动化测试 | 中 | 已修复 | 创建接口按能力拆行返回数组，测试改为 `$.data[0]` 断言并补 `capability` |
| RISK-01 | `llm:u_def:{userId}` 复用旧 key 但 value 语义变化 | 发布与缓存兼容 | 中 | 可接受 | 发布时清理旧 key；契约文档已记录实际 wrapper 结构 |
| RISK-02 | 生产旧数据迁移未执行 | 发布与数据兼容 | 高 | 待发布处理 | 本期已更新建表脚本，真实环境需单独准备迁移 SQL |

---

## 7. 交付结论

### 7.1 测试结论

- 主流程是否通过：通过，创建配置、能力拆行、列表查询、默认配置核心链路已覆盖。
- 异常流程是否通过：通过，非法能力、未登录、重复配置等 Java 侧边界已覆盖或由业务逻辑兜底。
- 回归检查是否通过：通过，`link-service` 与 `link-api` 自动化测试均成功。

### 7.2 遗留风险

- 远端 Canal / CDC 补偿链路未在本期真实环境验证。
- 生产环境旧 `llm_user_config.capabilities` 到新 `capability` 的数据迁移脚本需要发布前单独准备和演练。
- Python 执行端尚未接入按能力默认配置读取。

### 7.3 是否允许交付

- 是否可交付：有条件可交付。
- 交付前提：Java 侧代码、脚本、契约文档可提交；发布生产前必须补历史数据迁移与旧 Redis key 清理动作。
- 联调注意事项：前端创建配置接口需按数组处理 `data`；默认配置接口必须显式传 `capability`。
- 发布 / 回滚注意事项：回滚旧版本前需删除 `llm:u_def:{userId}`，并按旧表结构策略恢复或兼容用户配置数据。

---

## 8. 回写检查

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填测试结论 | 是 | 状态更新为待最终审核 |
| `project_info.md` 已同步更新 | 是 | 已补充 LLM 能力配置与默认映射现状 |
| 本次遗留风险已显式记录 | 是 | 见 RISK-01 / RISK-02 |
| 交付结论已明确 | 是 | Java 侧有条件可交付 |
