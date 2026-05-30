---
name: acceptance-generator
description: brief.md 已冻结后，基于 brief 生成 Gherkin 验收契约 .specs/<需求名>/acceptance.feature。
when_to_use: "用户要求生成 acceptance、Gherkin、验收场景、测试场景，且 .specs/<需求名>/brief.md 已冻结。"
---

# Acceptance Feature Generator

## 目标

把冻结的 `brief.md` 转成 `acceptance.feature`。该文件是验收契约，不是散文 PRD。

当前 Java 项目不引入 Cucumber/JBehave；`.feature` 用于约束 TD 和 JUnit/Mockito/MockMvc/SpringBootTest 测试。

## 前提校验

执行前必须确认：

1. `.specs/<需求名>/brief.md` 真实存在。
2. brief 已由开发者确认冻结（无"待确认问题"章节，或仅剩非阻塞项且用户确认保留）。
3. 用户明确要求生成 acceptance / Gherkin / 验收契约。

任一不满足，停止并说明，不允许基于聊天记忆凭空生成。

## 必读

1. `.specs/<需求名>/brief.md`
2. `.specs/<需求名>/feature_info.md`（若存在）
3. `.ai/skills/acceptance-generator/acceptance.template.feature`
4. 同业务域已有 acceptance（若存在）

## 输出

```text
.specs/<需求名>/acceptance.feature
```

同时更新 `feature_info.md`：状态、Scenario 总数、覆盖分类。

## 写作规则

- 每个 Scenario 对应一条业务规则，正交——两个 Scenario 不应是同一规则的不同参数（用 `Scenario Outline + Examples`）。
- `Given` 写具体前置状态。`When` 写具体触发动作。`Then` 写可机器断言的结果。
- `Then` 必须是可验证的具体状态：
  - ✅ `Then task.status == FAILED`
  - ✅ `Then 接口返回 400 错误码 INVALID_FILE_TYPE`
  - ✅ `Then MQ topic "tolink.rag.parse_task" 收到一条消息 file_id=F1`
  - ❌ `Then 系统应正确处理`（不可验证）
  - ❌ `Then 适当返回错误`（模糊）
- 写不出可断言的 `Then` 时，说明 brief 阶段该规则未定义清楚，停止并回到 `brief-generator` 补充。
- 重复参数化场景用 `Scenario Outline + Examples`，不允许复制粘贴。

## 颗粒度控制

- 单 `.feature` 文件目标 **10–25 个 Scenario**。
- 少于 8 个通常意味着覆盖不全；超过 30 个考虑是否需求范围过大或可拆分。
- 必须覆盖以下场景类型：
  - 主流程 happy path
  - brief 风险表中的异常场景（每条至少一个 Scenario）
  - 边界条件（空值、超限、非法输入）
  - 幂等与重试（可重复触发的操作）
  - 权限与状态转换（状态机合法/非法转换）

## 冻结

开发者确认后更新 `feature_info.md` 为 `acceptance 已冻结`，下一步进入 `technical-design`。
