# feature_info

| 项 | 值 |
| :--- | :--- |
| 需求名 | parse-result-consumer-resilience |
| 中文名 | Java 端 parse_result 消费接收兜底 |
| 来源 | GitHub issue ql-link/LinkRag-Service#15（修订版）、父需求 ql-link/LinkRag#44 |
| 分支 | feature/parse-result-consumer-resilience |
| 当前阶段 | 质量审查 APPROVE，待提交/PR |
| brief.md | 已冻结（2026-05-28） |
| acceptance.feature | 已冻结（2026-05-28） |
| technical_design.md | 已冻结（2026-05-28） |
| implementation_report.md | 已生成（2026-05-28） |

## acceptance 覆盖

| 分类 | Scenario 数 |
| :--- | :--- |
| ACK 治理（失败分类/重试/幂等/隔离缓存补偿） | 8 |
| SSE 当前任务过滤 | 3 |
| 卡住扫描 + 以 DB 为准补推 | 5 |
| 不变量（Java 不回写终态） | 1 |
| 合计（含 2 个 Scenario Outline） | 17 |

## 阶段记录

- brief 首版生成：基于 issue #15 修订版，已纠正原方案“Java 更新业务状态”的前提假设，范围收敛为三件纯 Java 消费侧工作（ACK 治理 / SSE 当前任务过滤 / 卡住观测告警）。
- MQ 细节敲定：核实 spring-kafka 2.7.4 默认 `SeekToCurrentErrorHandler`+`FixedBackOff(0L,9)`、无 DLQ、与缓存补偿共用默认工厂。已定方案——parse_result 单开专用容器工厂 + 错误处理器（隔离缓存补偿）、失败按"不可重试/暧昧/基础设施"分类、带退避重试、不加 DLQ、告警=日志+监控指标、不引入手动 ACK。
- 卡住扫描细节敲定：阈值默认 5 分钟（文件上传为在线等待场景）、按数据集覆盖；以 DB 为准——重读终态则补推 SSE、仍 created 则告警。
- brief 冻结（2026-05-28）：§5 转为"已冻结决策"，仅余实现级细节留待 TD。进入 acceptance 阶段。
- acceptance 冻结（2026-05-28）：17 个 Scenario（ACK 治理 8 / SSE 过滤 3 / 卡住扫描 5 / 不变量 1）评审通过。进入 technical_design 阶段。
- technical_design 冻结（2026-05-28）：决策锁定——引入 actuator(Micrometer)+spring-kafka-test，退避 3 次/1s/x2/10s。8 新增 + 4 修改文件，17 Scenario 全映射。进入 implementation-execution 阶段。
