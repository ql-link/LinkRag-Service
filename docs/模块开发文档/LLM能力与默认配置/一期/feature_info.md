# 功能信息卡

## 1. 基础信息

- 模块名称：LLM能力与默认配置
- 当前期次：一期
- 业务域：llm-config / provider / chat / dataset
- 当前状态：测试交付完成，待最终审核
- 复杂度等级：L3
- 当前分支：refactor/llm-capability-default-config

## 2. 功能摘要

- 背景：当前项目已经有系统厂商配置 `llm_system_provider`、用户配置 `llm_user_config` 和全局默认配置语义，但“模型能力”在系统厂商侧与用户配置侧的表达不统一，也无法支撑“一个用户每种能力一个默认配置”的核心业务要求。
- 目标：把 LLM 模型能力作为一等业务概念正式纳入 `llm-config` 模块，支持按能力查询可用厂商与模型、校验用户新增配置的能力合法性，并将 `llm_user_config` 收敛为“用户在某个能力下的一条模型配置”，从而直接支持每能力默认配置。
- 本期目标：
  - 明确系统厂商侧“模型 -> 能力列表”的业务语义。
  - 明确用户配置创建时的能力校验与按能力拆分落库规则。
  - 让 `llm_user_config` 一条记录只对应一个能力，支持每个用户按能力设置默认配置。
  - 为后续 `chat`、`dataset/embedding` 等调用链路提供按能力选择配置的统一业务基础。
- 本期不做：
  - 不在本期改造 Python 执行端能力选择逻辑。
  - 不在本期引入新的外部模型注册中心。
  - 不在本期扩展为多租户团队级默认模型策略。
  - 不在本期变更缓存一致性主范式，只补齐与能力维度配置相关的缓存对象。

## 3. 影响范围

- 关联模块：
  - `link-model`
  - `link-service`
  - `link-mapper`
  - `link-api`
  - `docs/db`
- 关联中间件：
  - MySQL
  - Redis
- 关联公共契约：
  - MySQL 表结构与唯一约束
  - Redis 默认配置 key 语义
  - LLM 能力枚举命名
  - 用户侧默认配置读取规则

## 4. 文档清单

- `feature_info.md`
- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`

## 5. 关联功能

- 上游依赖：
  - `llm_system_provider.supported_models`
  - `llm_user_config`
  - `AuthContext`
- 下游使用场景：
  - 用户添加 LLM 配置
  - 用户查询自己的配置列表
  - 用户按能力选择默认配置
  - 对话默认模型选择
  - 数据集 Embedding 模型选择
- 当前主要问题：
  - 系统厂商能力表达在代码中仍以 `String supportedModels` 承载，缺少清晰业务语义。
  - `llm_user_config` 当前仍偏向“一条记录承载多个能力”，无法自然表达按能力默认。
  - 用户配置创建链路缺少“模型是否支持目标能力”的校验与按能力拆分逻辑。

## 6. 推荐阅读顺序

1. `feature_info.md`
2. `requirement.md`
3. `AGENTS.md`
4. `project_info.md`
5. `docs/组件和数据库约定/middleware_contract.md`
6. `docs/db/schema.sql`
7. `docs/db/init.sql`

## 7. 后续回填项

- 需求阶段待确认：
  - 能力枚举集合的首批范围
  - 前端展示是否按模型聚合多能力记录
- 技术设计阶段待明确：
  - `POST /api/v1/llm/configs` 返回值从单个 DTO 调整为列表是否需要前端同步排期
  - 能力首批枚举是否需要补充 `IMAGE`、`AUDIO` 等更细能力
  - `llm:u_def:{userId}` 的 value 从单个默认配置调整为能力默认映射后，是否需要短期兼容旧值
- 实现完成后回填：
  - 实际表结构改动：`llm_user_config.capability VARCHAR(32)` 单能力字段已写入 `docs/db/init.sql`
  - 实际接口改动：新增用户侧厂商模型查询、按能力默认读取与设置接口，创建配置返回能力记录列表
  - 实际缓存 key 与回收策略：`llm:cfg:{configId}` 与 `llm:u_def:{userId}` 已通过 owner service 接入读保护与同步删缓存
  - 测试与交付结论：`link-service`、`link-api` 自动化测试通过，Java 侧有条件可交付
