# toLink-Service AI 协作开发宪法

## 1. 文档定位

`AGENTS.md` 是本项目的开发宪法，用于约束 AI 与人的协作方式、阶段门禁、文档产物、读取顺序与交付规则。

本文件只写稳定规则，不记录项目动态现状。项目现状统一维护在 `project_info.md`。

## 2. AI 固定入口

AI 在接收到任何新功能、改造、重构、流程调整请求时，必须优先读取以下文件：

1. `AGENTS.md`
2. `project_info.md`

若任务涉及跨模块数据、缓存、消息、对象存储、接口约定，还必须读取：

3. `docs/组件和数据库约定/middleware_contract.md`
4. `.agents/skills/middleware-contracts/SKILL.md`

若任务已经绑定到某次模块开发目录，还必须读取：

5. `docs/模块开发文档/<模块名称>/<phase>/feature_info.md`

## 3. 开发流程总览

项目功能开发遵循以下七阶段流程，禁止跳阶段直接进入实现：

1. 项目上下文加载
2. 需求分析
3. 技术设计
4. 代码实现
5. 测试与交付
6. 评审与质量门禁
7. 最终审核与提交/合并/发布

## 4. 意图到 Skill 映射

`AGENTS.md` 是流程总入口，负责把用户意图映射到正确的 skill。`project_info.md` 不负责流程编排，只负责提供项目现状上下文。

### 4.1 基本映射

| 用户意图 | 首选 skill | 后续 skill |
| :--- | :--- | :--- |
| 新需求、重构、范围不清 | `project-bootstrap` | `requirement-analysis` |
| 已明确需求，准备写需求文档 | `requirement-analysis` | `technical-design` |
| 需求已过审，准备写技术方案 | `technical-design` | `implementation-execution` |
| 技术方案已过审，准备写代码 | `implementation-execution` | `test-and-delivery` |
| 准备测试、交付、更新项目现状 | `test-and-delivery` | `review-and-quality` |
| 需要正式评审需求、技术、代码或交付结果 | `review-and-quality` | 最终审核 |
| 涉及中间件或跨模块规则 | `middleware-contracts` | `review-and-quality` |
| 需要校验是否触碰公共契约 | `middleware-contracts` | `review-and-quality` |
| 技术方案已明确，准备拆成执行任务 | `implementation-execution` | `test-and-delivery` |
| 遇到 bug、失败、异常行为 | `systematic-debugging` | `implementation-execution` |
| 需要先写失败测试再实现 | `test-driven-development` | `implementation-execution` |
| 准备宣称“完成/通过/可提交” | `review-and-quality` | 最终输出 |

### 4.2 执行规则

- 只要用户意图明显匹配某个 skill，就必须先进入该 skill，而不是直接跳到实现
- 若一个任务同时匹配多个 skill，按阶段先后顺序执行，不并行跳步
- 若任务既涉及中间件规则又涉及主流程阶段，先走主流程 skill，再在对应阶段补走 `middleware-contracts`
- 若遇到 bug、失败或异常行为，优先进入 `systematic-debugging`，不要先猜修复方案
- 若准备宣称完成，必须在 `review-and-quality` 中补做完成前验证

### 4.3 标准执行顺序

对常规功能开发，默认执行顺序为：

1. `project-bootstrap`
2. `requirement-analysis`
3. `technical-design`
4. `implementation-execution`
5. `test-and-delivery`
6. `review-and-quality`

## 5. 人机协作原则

### 5.1 AI 负责的事项

- 读取上下文文档并建立任务背景
- 将模糊需求整理成结构化分析结果
- 产出需求、技术、实现、测试交付文档草稿
- 按确认后的技术方案落地代码
- 汇总本次改造影响、测试结果与交付说明

### 5.2 人负责的事项

- 确认需求目标、范围、优先级与复杂度等级
- 回答关键业务问题与边界问题
- 审核需求文档、技术文档、测试与交付文档
- 对重要取舍、风险接受、阶段放行做最终决定
- 决定是否提交、合并或发布

### 5.3 强制人工确认点

以下节点必须人工确认后才能进入下一阶段：

1. 复杂度等级确认
2. `requirement.md` 审核通过
3. `technical_design.md` 审核通过（L2/L3）
4. `testing_delivery.md` 审核通过

### 5.4 需求讨论优先原则

在用户刚提出需求时，AI 不允许立刻进入复杂度分析、一期二期判断、目录创建或正式文档编写。

必须先进入“需求讨论阶段”，与用户充分确认需求边界。只有当需求讨论达到足够明确后，才允许：

- 建议复杂度等级
- 建议是否拆分 `一期 / 二期`
- 给出文档计划
- 创建模块目录与期次目录
- 编写 `feature_info.md`
- 编写 `requirement.md`

### 5.5 需求讨论阶段必澄清清单

在进入复杂度分析前，AI 至少要与用户确认以下维度中会影响方案方向的内容：

1. 需求目标
2. 本期范围与明确不做范围
3. 主业务维度
4. 参与角色
5. 主流程
6. 关键异常流程
7. 是否拆分 `一期 / 二期`
8. 各端或各系统职责边界
9. 关键状态与结果
10. 需要哪些数据对象
11. 外部依赖与中间件依赖
12. 兼容或迁移要求
13. 验收标准

如果以上维度仍存在关键空白，AI 应继续提问，不得提前进入正式需求分析。

## 6. 复杂度分级

复杂度等级在需求讨论充分、关键边界明确后由 AI 提出建议，最终由用户确认。未确认等级前，不允许进入正式需求文档编写。

### 6.1 L1 轻量功能

适用条件：

- 单模块或局部逻辑小改动
- 不涉及数据库结构变更
- 不新增或重构 Redis/MQ/OSS 契约
- 风险较低，验证范围较小

必须产出：

- `feature_info.md`
- `requirement.md`

可选补充：

- 在 `feature_info.md` 中补充实现摘要与测试结论

### 6.2 L2 标准功能

适用条件：

- 一个完整功能点
- 涉及多个代码模块协作
- 需要明确技术方案与测试方案
- 可能修改已有接口、DTO、Service、Mapper 或缓存逻辑

必须产出：

- `feature_info.md`
- `requirement.md`
- `technical_design.md`
- `testing_delivery.md`

### 6.3 L3 重型功能

适用条件：

- 跨模块或跨中间件改造
- 涉及 MySQL、Redis、MQ、OSS、外部系统中的一个或多个
- 存在迁移、兼容性、回滚、幂等、一致性等复杂问题
- 实际实现可能与技术方案存在显著偏差，需要单独沉淀改造结果

必须产出：

- `feature_info.md`
- `requirement.md`
- `technical_design.md`
- `implementation_report.md`
- `testing_delivery.md`

### 6.4 等级升级与降级

- AI 在后续分析中发现影响面扩大时，可以提出升级建议，并说明原因
- 等级升级必须得到用户确认
- 等级降级也必须得到用户确认

## 7. 模块开发目录规范

每次模块开发先创建模块目录，再按需要拆分阶段子目录。

目录结构如下：

```text
docs/模块开发文档/<模块名称>/<phase>/
```

示例：

```text
docs/模块开发文档/文件上传与解析/
├── 一期/
│   ├── feature_info.md
│   ├── requirement.md
│   ├── technical_design.md
│   ├── implementation_report.md
│   └── testing_delivery.md
└── 二期/
    ├── feature_info.md
    ├── requirement.md
    ├── technical_design.md
    ├── implementation_report.md
    └── testing_delivery.md
```

其中：

- `<模块名称>` 使用中文命名，直接表达模块业务含义，如 `文件上传与解析`、`用户管理`、`LLM配置管理`
- `<phase>` 默认从 `一期` 开始；若需求分析认为范围过大或适合拆阶段，再增加 `二期`、`三期`

### 7.1 是否拆分一期/二期的判断规则

在正式编写 `requirement.md` 前，AI 必须先判断当前模块开发是否需要拆成 `一期 / 二期`。

优先建议拆阶段的情况包括：

- 一次需求包含多个可以独立交付的功能块
- 一期可先交付基础能力，二期才补增强能力或复杂联动
- 范围过大，放在一个阶段会导致需求、设计和测试边界失控
- 依赖外部系统、数据迁移或复杂中间件改造，适合分步推进

若判断需要拆分，AI 必须：

1. 在需求初判中明确提出“建议拆分一期/二期”
2. 说明每一期的目标边界
3. 待用户确认后，在对应模块目录下创建 `一期/二期` 子目录
4. 当前阶段的所有过程文件只放在当前期次目录中

若判断不需要拆分，则只创建当前期次目录，默认使用 `一期`

## 8. 阶段目录文档职责

### 8.1 `feature_info.md`

定位：当前模块某一期目录的导航页。

职责：

- 记录模块名称、当前期次、当前状态、复杂度等级、当前分支
- 记录关联模块、关联中间件、关联历史功能
- 记录本次要求产出的文档
- 给出当前推荐阅读顺序

### 8.2 `requirement.md`

定位：需求分析文档。

职责：

- 说明做什么、为什么做、给谁用
- 明确边界、主流程、异常流程、验收标准
- 只写需求，不写实现方案

### 8.3 `technical_design.md`

定位：技术实现文档。

职责：

- 说明准备如何落地实现
- 明确涉及模块、接口、数据、缓存、消息、事务、权限与测试策略
- 必须基于真实代码与现有组件

### 8.4 `implementation_report.md`

定位：改造报告。

职责：

- 说明实际改了哪些模块、文件、接口、配置、表、缓存、消息
- 说明与技术方案的偏差及原因

### 8.5 `testing_delivery.md`

定位：测试与交付文档。

职责：

- 汇总测试范围、测试环境、测试用例、执行结果、遗留风险与交付注意事项

## 9. 阶段读取顺序与产物

### 9.1 阶段一：项目上下文加载

AI 必读：

1. `AGENTS.md`
2. `project_info.md`
3. `.agents/skills/middleware-contracts/SKILL.md`（若涉及中间件或跨模块约定）
4. `docs/组件和数据库约定/middleware_contract.md`（若涉及中间件或跨模块约定）

AI 输出：

- 需求理解摘要
- 当前已明确范围
- 当前仍待澄清问题
- 进入需求讨论阶段

人工动作：

- 确认需求理解
- 补充需求细节并参与讨论

### 9.1.1 阶段一补充：需求讨论

在本阶段，AI 必须围绕“需求讨论阶段必澄清清单”持续与用户确认细节。

只有当关键维度已足够明确后，AI 才允许补充输出：

- 影响面初判
- 建议复杂度等级
- 是否建议拆分 `一期 / 二期`
- 预计文档集合

人工动作：

- 确认复杂度等级
- 确认是否拆分 `一期 / 二期`

### 9.2 阶段二：需求分析

AI 必读：

1. 当前模块当前期次目录 `feature_info.md`
2. 同业务域或相近历史功能文档
3. 必要的业务背景文档

AI 产出：

- `requirement.md`
- 是否建议拆分为 `一期 / 二期`
- 若拆分，明确本期目标边界并在模块目录下创建对应期次子目录

人工动作：

- 回答关键需求问题
- 审核并放行需求文档

### 9.3 阶段三：技术设计

AI 必读：

1. 本期 `requirement.md`
2. `.agents/skills/middleware-contracts/SKILL.md`
3. `docs/组件和数据库约定/middleware_contract.md`
3. 对应组件说明文档
4. 相关模块真实代码

强制要求：

- 只要技术方案涉及某个组件、某个模块、某段既有链路，就必须先读取对应代码或文档
- 不允许仅凭历史记忆、命名推测或通用经验直接写方案
- 若引用 Redis、OSS、MQ、鉴权、加密等通用能力，必须先读对应组件说明文档
- 若引用业务模块能力，必须先读对应 Controller / Service / Model / Mapper 中至少一个真实入口
- 若未找到对应代码或文档，必须在技术文档中明确写出“已检查范围”和“当前未知项”

AI 产出：

- `technical_design.md`（L2/L3）

人工动作：

- 审核技术路线与关键取舍
- 放行技术文档

### 9.4 阶段四：代码实现

AI 必读：

1. 本期 `technical_design.md`（L2/L3）
2. 当前期次 `feature_info.md`
3. 对应组件说明文档

AI 产出：

- 代码改动
- `implementation_report.md`（L3 或复杂偏差场景）

人工动作：

- 对关键偏差、范围变化、风险升级做确认

### 9.5 阶段五：测试与交付

AI 必读：

1. `requirement.md`
2. `technical_design.md`（若存在）
3. `implementation_report.md`（若存在）
4. 实际测试结果

AI 产出：

- `testing_delivery.md`
- 更新 `project_info.md`

人工动作：

- 审核测试覆盖、遗留风险与交付结论

### 9.6 阶段六：评审与质量门禁

AI 必读：

- 当前阶段对应文档
- 相关代码改动
- 必要的公共契约与组件说明文档

AI 产出：

- 问题清单
- 风险清单
- 待确认项

人工动作：

- 根据评审结果决定是否允许进入最终审核

### 9.7 阶段七：最终审核与提交/合并/发布

AI 必须汇总：

- 本次模块当前期次目录全部产物
- 代码改动摘要
- 风险与交付建议

人工动作：

- 决定是否提交、合并、发布

## 10. 跨模块约定与组件说明

### 10.1 `docs/组件和数据库约定/middleware_contract.md`

只记录跨模块统一契约，例如：

- MySQL 命名与公共字段约定
- Redis key 与 TTL 约定
- MQ topic、group、消息结构与幂等约定
- OSS bucket/path/public-private 约定
- API 响应、错误码、traceId、日志字段约定

### 10.2 `docs/组件和数据库约定/middleware-components/*.md`

用于记录组件说明书，介绍：

- 组件职责
- 适用场景
- 当前实现结构
- 核心类与配置项
- 已有能力与使用方式
- 限制与注意事项

组件说明文档不替代某次需求的技术方案。

## 11. 代码与文档原则

- 技术文档必须引用真实代码、模块或组件，不允许脱离代码现状空想
- 涉及异常类、错误码和统一响应时，必须参考现有异常体系与代码规范，不允许另起一套命名和编码风格
- 涉及 Redis、OSS、MQ 等中间件的设计或编码时，必须先读取对应中间件组件说明文档，再决定接入方式和代码放置位置
- 需求文档与技术文档边界必须清晰，不允许重复描述
- 改造报告只记录实际落地结果与差异，不重复写需求和设计
- 测试与交付文档必须以实际验证结果为依据，不允许只写计划不写结果
- 每次功能开发完成后，必须同步更新 `project_info.md`
- 编写代码时，关键部分必须补充注释，尤其是：
  - 复杂业务判断
  - 关键状态流转
  - 跨组件调用链路
  - 不易一眼看懂的设计意图
- 注释应解释“为什么这样做”或“这一段在保障什么”，不要写无意义的逐行翻译式注释

## 12. Skill 使用原则

本项目的 skill 用于驱动读取顺序、阶段切换与文档产出，不替代项目文档本身。

推荐 skill 体系：

- `project-bootstrap`
- `requirement-analysis`
- `technical-design`
- `implementation-execution`
- `test-and-delivery`
- `review-and-quality`
- `middleware-contracts`

各 skill 的职责定义以 `.agents/skills/*/SKILL.md` 为准。
