---
name: code-review-and-quality
description: 实现和测试完成后，提交或合并前执行质量门禁。
when_to_use: "代码写完了 review、提交前检查、准备合并、质量审查。"
---

# Code Review And Quality

## 必读

1. `AGENTS.md`
2. `docs/<需求名>/brief.md`
3. `docs/<需求名>/acceptance.feature`
4. `docs/<需求名>/technical_design.md`
5. 实际 `git diff`
6. 测试结果

## 审查维度

- Correctness：是否满足 Scenario
- Tests：是否覆盖主流程、异常、边界、幂等和回归
- Architecture：是否遵循 Maven 模块边界和组件复用
- Security：认证、权限、敏感信息、内部接口
- Performance：无界查询、N+1、同步阻塞、缓存误用
- Contracts：API、MySQL、MQ、Redis、OSS 文档是否同步

## 输出

按严重程度输出问题：

- Critical：阻塞合并
- Required：必须修复
- Suggestion：建议优化

结论使用 `APPROVE` 或 `REQUEST_CHANGES`。
