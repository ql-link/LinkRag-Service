---
name: technical-design
description: brief.md 和 acceptance.feature 已冻结后，生成 docs/<需求名>/technical_design.md；必须基于真实 Java 代码、组件文档和契约。
when_to_use: "用户要求生成技术方案、technical_design.md、技术实现文档，且 brief + acceptance 已冻结。"
---

# Technical Design

## 目标

把 `brief.md` 与 `acceptance.feature` 转成可实现的 `technical_design.md`。

## 必读

1. `AGENTS.md`
2. `project_info.md`
3. `docs/<需求名>/brief.md`
4. `docs/<需求名>/acceptance.feature`
5. `docs/<需求名>/feature_info.md`（若存在）
6. `.ai/skills/technical-design/technical_design.template.md`
7. 相关 architecture/reference/guides 文档
8. 相关 Controller / Service / Entity / Mapper / 组件真实代码

## 输出

```text
docs/<需求名>/technical_design.md
```

## 必须包含

- 设计目标与非目标
- 当前系统分析
- 改动范围和文件树
- API / 数据 / Redis / MQ / OSS / 配置影响
- 方法级变更总表：每个新增/修改方法必须关联至少一个 Scenario
- 逐方法实现方案
- Scenario 覆盖自检：每个 Scenario 对应实现点和测试点
- 测试方案：JUnit/Mockito/MockMvc/SpringBootTest 命令与用例映射
- 发布、回滚、风险与待确认项

## 强制规则

- `[修改]` 文件和方法必须真实存在。
- 不能凭命名猜测；必须读代码。
- 涉及公共契约时同步调用 `contract-guard` 思路检查。
- 旧 `requirement.md` 不再作为输入。
