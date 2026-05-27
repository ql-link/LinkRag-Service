---
name: auto-test
description: 为 toLink-Service 生成或修复 JUnit、Mockito、MockMvc、SpringBootTest 测试。
when_to_use: "写测试、补测试、修复失败测试、提升覆盖率、为 Scenario 落测试。"
---

# Auto Test

## 目标

为 Java/Spring Boot 代码生成可运行、可维护的测试。

## 规则

- 单元测试默认 Mock MySQL、Redis、MQ、OSS、外部 HTTP。
- Controller 测试优先使用 MockMvc 和测试安全配置。
- Service 测试重点覆盖业务分支、异常、权限、缓存/MQ/OSS 交互。
- 每个 acceptance Scenario 至少映射到一个测试或测试组。

## 输出

先说明测试策略，再修改或新增测试代码，并运行对应命令：

```bash
mvn -pl <module> test
```
