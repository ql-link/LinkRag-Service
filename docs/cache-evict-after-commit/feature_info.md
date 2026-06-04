# feature_info

| 项 | 值 |
| :--- | :--- |
| 需求名 | cache-evict-after-commit |
| 中文名 | 缓存首删时机调整为事务提交后执行 |
| 来源 | Linear issue LINKS-15「延迟双删优化建议」 |
| 分支 | feature/cache-evict-after-commit（待创建） |
| 当前阶段 | 实现已完成（2026-06-02），待代码评审 |
| brief.md | 已冻结（2026-06-02） |
| acceptance.feature | 已冻结（2026-06-02，14 Scenario） |
| technical_design.md | 已冻结（2026-06-02） |
| implementation_report.md | 未开始 |

## 范围判断

| 项 | 本需求处置 |
| :--- | :--- |
| 主流程第一次删缓存 | **本次范围**：事务内改为 afterCommit，无事务保持立即删除 |
| CDC / MQ 补偿第二删 | **保留不变** |
| 缓存 key 路由 | 不改 |
| 缓存目标覆盖范围 | 不改 |
| 读路径缓存回填策略 | 不改 |
| 直接更新缓存 / 强一致方案 / 分布式锁 | 不做 |
| 首删失败语义 | **已决策**：只要数据库写成功，首删失败统一走日志/指标 + 补偿链路收敛，不改请求结果 |
| 同事务多次删缓存去重 | **方向已定**：倾向事务级聚合去重，具体方案留待 TD |

## 阶段记录

- 2026-06-02 需求进入 Spec-as-Test：基于 Linear issue LINKS-15 启动“缓存首删时机优化”需求分析。
- 2026-06-02 代码核实现状：确认当前统一删缓存入口在缓存组件层立即执行删除；`UserLLMConfigServiceImpl` 等事务写路径在事务方法体内直接触发首删；`CacheReadProtectionService` 在 miss 时会回源 DB 并回填缓存，因此“事务未提交但缓存已删”会放大旧值回填风险。
- 2026-06-02 代码核实补偿链路：确认 `tolink.cache.evict` 补偿删除链路已存在且职责清晰，本次不改 topic/消息/消费者，只优化首删时机。
- 2026-06-02 代码核实项目先例：确认删除通知、异步上传提交、解析投递均已使用 `afterCommit` 模式，说明本需求可复用现有事务同步范式，无需引入新机制。
- 2026-06-02 首版 brief：初步收敛为“有事务 afterCommit 首删、无事务立即删、保留补偿第二删、业务层无侵入”的方向，并识别两类待确认点：首删失败语义、同事务多次删缓存是否做 key 聚合去重。
- 2026-06-02 brief 冻结决策：用户确认 3 项关键结论——①只要数据库写成功，首删失败统一采用“日志/指标 + 补偿链路收敛”，不回头改变请求结果；②倾向同事务内做待删 key 聚合与去重，具体方案留待 TD；③brief 可冻结并进入 acceptance 阶段。
- 2026-06-02 acceptance 首版：基于冻结 brief 生成 14 个 Scenario，覆盖事务提交时序、事务回滚、提交前并发读、无事务兼容、补偿第二删不回归、数据库写成功后首删失败统一不改请求结果、多 key 删除结果正确等核心规则。
- 2026-06-02 acceptance 冻结决策：用户确认“只要数据库写已成功，首删失败不再改变请求结果”适用于事务与无事务路径，并同意进入技术方案阶段。
- 2026-06-02 technical design 首版：基于冻结 brief + acceptance + 真实代码生成 `technical_design.md`，方案收敛为“缓存组件内完成事务感知首删、事务级 key 聚合去重、补偿第二删强失败语义保留、`syncDeleteRequired` 仅兼容保留不再控制首删抛错”。
- 2026-06-02 technical design 冻结决策：用户确认补偿第二删仍保留强失败语义，并要求进入实现阶段。
- 2026-06-02 实现完成：`CacheConsistencyService` 已改为事务感知首删；补充 `CacheConsistencyServiceTest`、`CacheCompensationMQReceiverTest`；同步更新 `cache_module.md`、`configuration.md`、`testing.md`。最小回归 `mvn -pl link-service -am -DfailIfNoTests=false -Dtest=CacheConsistencyServiceTest,CacheCompensationMQReceiverTest test` 通过，`check_ai_links` 与 `check_docs_sync --working` 通过。

## acceptance 覆盖

| 分类 | Scenario 数 |
| :--- | :--- |
| 一、事务路径：提交/回滚/并发读时序 | 5 |
| 二、非事务路径兼容 | 2 |
| 三、补偿第二删保留 | 3 |
| 四、数据库写成功后首删失败统一收敛 | 2 |
| 五、多 key 与重复触发结果正确 | 2 |
| 合计 | 14 |

## 下一步

- 进入代码评审；若无调整，再决定是否扩大到更大范围 Maven 回归测试。
