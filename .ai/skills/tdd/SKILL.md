---
name: tdd
description: 用户明确要求 TDD、先写测试、红绿重构时使用。
when_to_use: "TDD、先写测试、红绿重构、Red Green Refactor。"
---

# TDD

## 流程

1. 拆解最小测试清单。
2. Red：先写一个失败的 JUnit/Mockito/MockMvc 测试。
3. Green：写最小实现让测试通过。
4. Refactor：在测试保护下清理实现。
5. 重复直到覆盖 acceptance Scenario。

## 规则

- 没有失败测试，不写生产代码。
- 单步只覆盖一个行为。
- 外部依赖默认 Mock。
- 测试命名表达业务行为。
