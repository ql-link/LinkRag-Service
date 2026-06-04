---
name: code-review-and-quality
description: 实现和测试完成后，提交或合并前执行质量门禁。
when_to_use: "代码写完了 review、提交前检查、准备合并、质量审查。"
---

# Code Review And Quality

## 必读

1. `AGENTS.md`
2. `.specs/<需求名>/brief.md`
3. `.specs/<需求名>/acceptance.feature`
4. `.specs/<需求名>/technical_design.md`
5. `.specs/<需求名>/implementation_report.md`（若存在）
6. 实际 `git diff`
7. 测试结果

## 审查流程

### 步骤 1：先核对测试证据

- 对照 `acceptance.feature` 中的 Scenario，确认每条都有对应测试且通过。
- 若关键 Scenario 无测试覆盖，直接记为 Required 问题，不继续往下走。

### 步骤 2：六维审查

| 维度 | 检查要点 |
| --- | --- |
| Correctness | 是否满足所有 Scenario，异常路径是否可用 |
| Tests | 是否覆盖主流程、异常、边界、幂等，回归是否通过 |
| Architecture | 是否遵循 Maven 模块边界和组件复用约定 |
| Security | 认证、权限、敏感信息、内部接口防护 |
| Performance | 无界查询、N+1、同步阻塞、缓存误用 |
| Contracts | API、MySQL、MQ、Redis、OSS 文档是否已同步 |

## 问题分级

- `Critical`：阻塞合并（安全漏洞、数据风险、核心功能错误）
- `Required`：必须修复后再合并（需求偏差、关键测试缺失、明显架构违规）
- `Suggestion`：可优化项，不阻塞当前交付

结论输出 `APPROVE` 或 `REQUEST_CHANGES`。

## 阶段门禁输出

审查结束必须输出状态块：

```text
PHASE: 最终审核与提交/合并
AI_REVIEW: PASS | FAIL
HUMAN_REVIEW: APPROVED | PENDING
NEXT_PHASE: 提交/合并 | BLOCKED
BLOCK_REASON: <阻塞原因；无则写 NONE>
```

判定规则：

- 存在 `Critical` 或 `Required` 未关闭 → `AI_REVIEW=FAIL`
- 无阻塞问题且结论为 `APPROVE` → `AI_REVIEW=PASS`
- 仅当 `AI_REVIEW=PASS` 且 `HUMAN_REVIEW=APPROVED` 才允许进入提交/合并
