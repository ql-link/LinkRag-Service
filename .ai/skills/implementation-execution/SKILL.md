---
name: implementation-execution
description: brief、acceptance、technical_design 已审核后，按方案实现 Java 代码和测试。
when_to_use: "开始写代码、按方案实现、执行 technical_design、实现这个需求。"
---

# Implementation Execution

## 目标

按已审核的 `technical_design.md` 落地代码，并用 `acceptance.feature` 约束测试覆盖。

## 必读

1. `AGENTS.md`
2. `project_info.md`
3. `docs/<需求名>/feature_info.md`
4. `docs/<需求名>/brief.md`
5. `docs/<需求名>/acceptance.feature`
6. `docs/<需求名>/technical_design.md`
7. 相关真实代码和契约文档

## 输出

- Java 代码改动
- JUnit/Mockito/MockMvc/SpringBootTest 测试
- 必要时产出 `docs/<需求名>/implementation_report.md`

## 规则

- 不扩大 acceptance 和 TD 边界。
- 若实现偏离 TD，必须记录原因和影响。
- 不再读取或生成 `requirement.md`、`testing_delivery.md`。
- 关键业务判断、状态流转、跨组件调用需要补充“为什么”的注释。
- 完成后运行匹配范围测试与 doc-sync 校验，不直接宣称可发布，需进入质量审查。
