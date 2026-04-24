---
name: test-and-delivery
description: 为开发者或测试执行者生成可直接执行的测试单，记录测试结果、遗留风险和交付结论，并更新 project_info.md。
---

# Test And Delivery

## 目的

在实现完成后，形成开发者可直接执行的测试单，并在执行后沉淀可审核的交付结论，同时同步项目现状。

## 输入前提

- 代码实现已完成
- 当前模块当前期次目录文档已基本齐备

## 必读文件

1. `AGENTS.md`
2. `project_info.md`
3. 当前模块当前期次目录 `feature_info.md`
4. `requirement.md`
5. `technical_design.md`（若存在）
6. `implementation_report.md`（若存在）
7. 已有实际测试结果或待补充的测试执行信息

## 输出位置

- `docs/module-development-files/<domain>-<module-name>/<phase>/testing_delivery.md`
- 更新 `project_info.md`

测试与交付文档模板固定为：

`.agents/skills/test-and-delivery/testing_delivery.template.md`

## 输出要求

- 测试范围
- 测试前提与环境准备
- 可执行的测试步骤/测试用例
- 实际执行结果
- 问题记录与处理情况
- 遗留风险
- 交付结论

写作时应优先按 `.agents/skills/test-and-delivery/testing_delivery.template.md` 的章节结构落文。

## 强制约束

- 文档应优先服务开发者和测试执行者，必须写成“可照着执行”的形式
- 不允许只写测试计划而不写实际结果
- 在宣称本次功能完成前，必须确认 `project_info.md` 已同步更新
