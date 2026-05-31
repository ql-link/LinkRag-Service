# Spec-as-Test 工作流说明

## 为什么替换旧流程

旧七阶段流程中的 `requirement.md`、`technical_design.md`、`testing_delivery.md` 容易重复描述同一事实。自然语言需求也容易留下“正确处理”“适当返回”等模糊空间。

新版流程把业务规则前置为可断言的 Scenario：

```text
原始需求
  -> brief.md
  -> acceptance.feature
  -> technical_design.md
  -> Code + Tests
```

## 三个产物的边界

| 产物 | 唯一职责 |
| --- | --- |
| `brief.md` | 为什么做、业务流程、模块实现思路 |
| `acceptance.feature` | 什么算做对了 |
| `technical_design.md` | 在哪里改、怎么改、怎么测 |

## 项目约定

- 新需求默认放在 `docs/<需求名>/`。
- 旧七阶段模块文档目录已移除，不再转换或引用。
- acceptance 不直接驱动 Cucumber；Java 测试需要在命名、注释或 TD 映射中承接 Scenario。
- 如果 Scenario 难以测试，优先回到 acceptance 或 TD 修订，而不是绕过验收契约。
