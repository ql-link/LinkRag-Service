# Spec-as-Test 工作流使用手册

toLink-Service 使用 `brief.md → acceptance.feature → technical_design.md → Code + Tests`。

## 0. 需求进入方式

默认先把需求登记成 issue，再进入 Spec-as-Test：

- 需要 Linear 与 GitHub 双向同步时，使用 `cowork-issue-sync`
- 只需要 GitHub issue 时，使用 `issue-writer`

issue 建立后，再进入 `brief.md` 阶段梳理范围、边界和风险。

## 1. brief.md

路径：`docs/<需求名>/brief.md`

职责：

- 说明做什么、为什么做、本次不做什么
- 说明业务流程、关键异常和涉及模块
- 说明模块实现思路，但不到代码层
- 列出具体风险和待确认问题

冻结条件：

- 阻塞性待确认问题已收敛
- 开发者明确确认“冻结”或“进入下一阶段”

## 2. acceptance.feature

路径：`docs/<需求名>/acceptance.feature`

职责：

- 用 Gherkin 描述“什么是做对了”
- 每个 Scenario 表达一条业务规则
- 每个 `Then` 必须可断言

当前项目不引入 Cucumber/JBehave；`.feature` 是验收契约，测试由 JUnit、Mockito、MockMvc、SpringBootTest 承接。

## 3. technical_design.md

路径：`docs/<需求名>/technical_design.md`

职责：

- 基于 brief、acceptance 和真实代码说明在哪里改、怎么改
- 每个新增/修改方法关联至少一个 Scenario
- 明确 API、数据、缓存、MQ、OSS、事务、异常和测试策略

## 4. 实现与验收

实现时以 `technical_design.md` 为方案，以 `acceptance.feature` 为验收契约。提交前至少运行：

```bash
python3 scripts/check_ai_links.py
python3 scripts/check_docs_sync.py --working
mvn test
```

若全量测试受外部依赖影响失败，需记录失败原因，并补跑受影响模块的稳定测试。

## 5. 旧流程清理

旧七阶段模块文档和旧组件约定目录已移除。后续新增需求、技术设计和测试说明只使用 `docs/<需求名>/` 下的 Spec-as-Test 产物；跨模块稳定知识沉淀到 `docs/architecture`、`docs/reference`、`docs/guides` 或 `docs/development`。
