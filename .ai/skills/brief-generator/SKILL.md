---
name: brief-generator
description: 用户提出新需求、重构想法、业务分析、先写 brief 或需要理清影响模块时使用；产出 docs/<需求名>/brief.md，并支持迭代到冻结。
when_to_use: "新需求、先分析、写 brief、需求理解、业务流程梳理、影响模块分析。若 brief 已冻结且用户要生成 acceptance，转 acceptance-generator。"
---

# Brief Generator

## 目标

把原始需求整理成面向开发者的 `brief.md`。brief 只回答“为什么做、做什么、不做什么、业务怎么跑、涉及哪些模块、风险是什么”，不到代码实现层。

## 输出位置

```text
docs/<需求名>/brief.md
docs/<需求名>/feature_info.md
```

## 必读

1. `AGENTS.md`
2. `project_info.md`
3. 与需求相关的 architecture/reference/guides 文档
4. 相关 Controller / Service / Entity / Mapper / 组件入口
5. 同业务域新版 brief / acceptance / technical_design（若存在）

## brief 结构

```markdown
# <需求名> Brief

## 1. 需求摘要
- 做什么
- 为什么做
- 本次不做

## 2. 业务流程
### 2.1 主流程图
### 2.2 流程详解

## 3. 核心模块与实现思路
按模块说明位置、职责、复用能力、新增能力、上下游关系、关键决策。

## 4. 风险与不确定性
| 风险 / 问题 | 触发条件 | 影响 | 当前判断 / 应对方向 |

## 5. 待确认问题
仅迭代期保留；冻结时删除或由用户确认保留非阻塞项。
```

## 规则

- 先读真实代码和文档，再判断模块边界。
- 不写表字段、接口路径、类名、方法签名等代码层细节，除非为了定位现有模块。
- 风险必须落到具体场景，不写“注意稳定性”。
- 用户反馈后读取当前 brief，把修订写回对应章节，不追加问答记录。
- brief 冻结后更新 `feature_info.md` 状态，并提示下一步生成 `acceptance.feature`。
