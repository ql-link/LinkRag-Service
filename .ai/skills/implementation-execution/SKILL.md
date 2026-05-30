---
name: implementation-execution
description: brief、acceptance、technical_design 已审核后，按方案实现 Java 代码和测试。
when_to_use: "开始写代码、按方案实现、执行 technical_design、实现这个需求。"
---

# Implementation Execution

## 目标

按已审核的 `technical_design.md` 落地代码，并用 `acceptance.feature` 约束测试覆盖。

## 输入前提

- `docs/<需求名>/brief.md` 已冻结
- `docs/<需求名>/acceptance.feature` 已冻结
- `docs/<需求名>/technical_design.md` 已审核通过

## 必读

1. `AGENTS.md`
2. `project_info.md`
3. `docs/<需求名>/feature_info.md`
4. `docs/<需求名>/brief.md`
5. `docs/<需求名>/acceptance.feature`
6. `docs/<需求名>/technical_design.md`
7. 相关真实代码和契约文档

## 输出

- Java 代码改动（按 `technical_design.md` 中改动文件树落地）
- JUnit/Mockito/MockMvc/SpringBootTest 测试
- 必要时产出 `docs/<需求名>/implementation_report.md`

## 实施步骤

### 步骤 1：按文档落地代码

- 以 `acceptance.feature` 的 Scenario 为验收边界
- 以 `technical_design.md` 的方法级变更总表为实现依据
- 优先复用已有模块和组件，不扩大 acceptance 和 TD 边界
- 若发现 TD 描述有误或无法按原方案实现，记录偏差原因，不能只改代码不声不响

### 步骤 2：测试覆盖

每个 acceptance Scenario 至少映射到一个测试。运行：

```bash
mvn -pl <module> test        # 单模块
mvn test                     # 全量
```

### 步骤 3：契约同步

若本次改动涉及 API、MySQL、MQ、Redis、OSS 或错误码，代码完成后先执行 `doc-maintenance-sync`，再运行：

```bash
python3 scripts/check_docs_sync.py --working
```

| 改动类型 | 追加执行 |
| --- | --- |
| 新增或修改 HTTP 接口 | 执行 `swagger-annotation` 补注解 |
| 新建或修改数据库表 | 遵循 `mysql-ddl-conventions` 规范 |
| 新增或修改 MQ 消息 | 遵循 `mq-middleware` 规范并同步消息清单 |

### 步骤 4：判断是否写改造报告

以下任一情况必须产出 `docs/<需求名>/implementation_report.md`：

- 改动跨多个 Maven 模块或多个中间件
- 实现与 `technical_design.md` 有明显偏差（方案层面改变，不是细节调整）
- 涉及多状态机交互、异步链路、跨系统集成等复杂场景
- 存在需要向测试、交付、审查特别说明的实现差异

改动集中在单模块、实现与 TD 基本一致且影响面小时，可不写改造报告。

## 完成后的停点

本 skill 完成后不宣称可发布。完成动作：

1. 代码实现完成，测试通过
2. 必要时写好 `implementation_report.md`
3. 更新 `docs/<需求名>/feature_info.md`（状态：实现完成）
4. 进入 `code-review-and-quality`

## 约束

- 不扩大 acceptance 和 TD 边界。
- 若实现偏离 TD，必须记录原因，不能只改代码不留记录。
- 不再读取或生成 `requirement.md`、`testing_delivery.md`。
- 注释只写 WHY（隐藏约束、非显而易见的设计意图），不写 WHAT。
