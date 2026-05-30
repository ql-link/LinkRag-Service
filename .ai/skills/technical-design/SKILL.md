---
name: technical-design
description: brief.md 和 acceptance.feature 已冻结后，生成 .specs/<需求名>/technical_design.md；必须基于真实 Java 代码、组件文档和契约。
when_to_use: "用户要求生成技术方案、technical_design.md、技术实现文档，且 brief + acceptance 已冻结。"
---

# Technical Design

## 目标

把 `brief.md` 与 `acceptance.feature` 转成可落地的 `technical_design.md`。回答：改哪些文件、怎么改、为什么这样改、如何验证。

## 输入前提

1. `.specs/<需求名>/brief.md` 已冻结
2. `.specs/<需求名>/acceptance.feature` 已冻结
3. 用户明确要求生成技术文档

任一不满足，停止并说明，不允许基于聊天记忆直接生成。

## 必读

1. `AGENTS.md`
2. `project_info.md`
3. `.specs/<需求名>/brief.md`
4. `.specs/<需求名>/acceptance.feature`
5. `.specs/<需求名>/feature_info.md`（若存在）
6. `.ai/skills/technical-design/technical_design.template.md`
7. 相关 architecture/reference/guides 文档
8. 相关 Controller / Service / Entity / Mapper / 组件真实代码

组件文档强制规则：

| 涉及内容 | 必须先读 |
| --- | --- |
| Redis 缓存 | `docs/architecture/cache_module.md` |
| OSS / 文件存储 | `docs/architecture/object_storage_module.md` |
| MQ 消息 | `docs/reference/mq_contracts.md` + `mq-middleware` skill |
| 数据库表 | `docs/reference/mysql_schema.md` + `docs/db/schema.sql` |

## 输出

```text
.specs/<需求名>/technical_design.md
```

## 必须包含

- 设计目标与非目标
- 当前系统分析
- 改动范围与文件树（每个文件标注 `[新增]` / `[修改]` / `[删除]` / `[不改]`，并说明原因）
- API / 数据 / Redis / MQ / OSS / 配置影响
- 方法级变更总表：每个新增/修改方法必须关联至少一个 Scenario
- 逐方法实现方案
- Scenario 覆盖自检：`acceptance.feature` 中每个 Scenario 对应哪个实现点和测试点
- 测试方案：JUnit/Mockito/MockMvc/SpringBootTest 用例映射与执行命令
- 发布、回滚、风险与待确认项

## 工作流程

### 步骤 1：校验输入

确认 `brief.md` + `acceptance.feature` 存在且冻结，否则停止。

### 步骤 2：从 brief 和 acceptance 提取要素

- brief 第 2 章 → 主链路 + 异常分支
- brief 第 3 章 → 模块分工、复用边界、关键决策
- acceptance Scenario 列表 → 作为"必须满足的契约清单"，TD 中每个方法都应能对应到至少一条 Scenario

### 步骤 3：扫描真实代码

在落方案前必须确认：

- 改动文件树中每个 `[修改]` 文件是否真实存在
- 每个 `[新增]` 文件的父目录是否符合现有项目结构
- 方法级变更总表中每个 `[修改]` 方法是否真实存在
- 是否已有可复用组件 / Service / DTO

**未扫代码就写方案，TD 默认不合格。**

### 步骤 4：设计接口、数据、中间件

确定 API 设计、数据模型、缓存策略、消息结构、幂等/事务/一致性处理方式。

### 步骤 5：确认 Scenario 全覆盖

逐条 Scenario 自查：它由哪个方法实现？由哪个测试验证？若有 Scenario 无承接，必须在"风险与待确认项"中说明。

### 步骤 6：提问门禁

以下问题不清楚时，必须先向用户提问，不允许直接出 TD：

- 是否需要兼容旧接口或迁移旧数据
- 外部系统（Python 端、第三方）的协作方式不明确
- 涉及新增 MQ topic / Redis key 命名空间等公共契约
- 关键技术取舍需要用户决定
- acceptance 中存在无法对应到具体实现的 Scenario

只问会改变设计的关键问题，低风险假设写入"假设与依赖"不阻塞文档。

### 步骤 7：迭代收敛与冻结

用户审核后把修订写回对应章节，不追加在末尾。用户确认后更新 `feature_info.md` 为 `technical_design 已冻结`，告知下一步进入 `implementation-execution`。

## 约束

- `[修改]` 文件和方法必须真实存在，不能凭命名猜测。
- 涉及公共契约时同步调用 `contract-guard` 检查。
- 旧 `requirement.md` 不再作为输入。
- 不允许写进 TD 的内容：已实现的代码结果、测试执行结论、"适当增加校验"等抽象口号。
