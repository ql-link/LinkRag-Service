---
name: acceptance-generator
description: brief.md 已冻结后，基于 brief 生成 Gherkin 验收契约 docs/<需求名>/acceptance.feature。
when_to_use: "用户要求生成 acceptance、Gherkin、验收场景、测试场景，且 docs/<需求名>/brief.md 已冻结。"
---

# Acceptance Feature Generator

## 目标

把冻结的 `brief.md` 转成 `acceptance.feature`。该文件是验收契约，不是散文 PRD。

当前 Java 项目不引入 Cucumber/JBehave；`.feature` 用于约束 TD 和 JUnit/Mockito/MockMvc/SpringBootTest 测试。

## 必读

1. `docs/<需求名>/brief.md`
2. `docs/<需求名>/feature_info.md`（若存在）
3. `.ai/skills/acceptance-generator/acceptance.template.feature`
4. 同业务域已有 acceptance（若存在）

## 输出

```text
docs/<需求名>/acceptance.feature
```

同时更新 `feature_info.md`：状态、Scenario 总数、覆盖分类。

## 写作规则

- 每个 Scenario 对应一条业务规则。
- `Given` 写具体前置状态。
- `When` 写具体触发动作。
- `Then` 写可断言结果，例如状态值、返回码、数据库行、MQ 消息、缓存变化。
- 重复参数用 `Scenario Outline + Examples`。
- 覆盖主流程、异常、边界、幂等/重试、权限与状态转换。
- 不写实现细节、类名、表名、接口路径，除非它本身就是验收对象。

## 冻结

开发者确认后更新 `feature_info.md` 为 `acceptance 已冻结`，下一步进入 `technical-design`。
