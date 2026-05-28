# parse-result-consumer-resilience 实现报告

- **日期：** 2026-05-28
- **分支：** feature/parse-result-consumer-resilience
- **依据：** 已冻结 brief.md / acceptance.feature / technical_design.md

## 1. 落地改动

### 新增
| 文件 | 说明 |
| --- | --- |
| `link-service/.../service/exception/NonRetryableParseResultException.java` | 不可重试业务异常（不匹配类） |
| `link-service/.../service/exception/ParseResultPendingException.java` | 暧昧可重试异常（log 未就绪） |
| `link-service/.../service/support/ParseResultMetrics.java` | 监控指标封装，`ObjectProvider<MeterRegistry>` 注入，缺失降级日志 |
| `link-service/.../service/mq/config/ParseResultKafkaConfig.java` | parse_result 专用容器工厂 + `SeekToCurrentErrorHandler`（退避 3/1s/×2/10s + notRetryable 分类 + 告警/指标 recoverer） |
| `link-service/.../service/config/ParseResultStuckProperties.java` | `tolink.parse-result.stuck.*` 配置（默认 5m、按 dataset 覆盖） |
| `link-service/.../service/config/SchedulingConfig.java` | `@EnableScheduling`（项目首次引入调度） |
| `link-service/.../service/impl/know/KnowledgeParseStuckScanner.java` | 卡住扫描定时任务，以 DB 为准补推/告警 |

### 修改
| 文件 | 说明 |
| --- | --- |
| `KnowledgeParseResultServiceImpl.java` | 失败抛分类异常；通过后做当前任务过滤（相等推送 / 不等审计跳过 / 空指针 fail-open）；不写业务表 |
| `KnowledgeParseResultKafkaReceiver.java` | `@KafkaListener` 指向 `parseResultKafkaListenerContainerFactory` |
| `link-service/pom.xml` | 引入 `spring-boot-starter-actuator`、测试域 `spring-kafka-test` |
| `application-dev.yml` / `application-local.yml` | `tolink.parse-result.stuck.*` 默认值 |
| `.env.example` | `PARSE_RESULT_STUCK_THRESHOLD`、`PARSE_RESULT_STUCK_SCAN_INTERVAL_MS` |
| 文档同步 | `mq_contracts.md`、`mq_module.md`、`configuration.md`、`knowledge_file_module.md`、`integration.md`、`testing.md`、`project_structure.md`、`README.md` |

### 测试
| 文件 | 覆盖 |
| --- | --- |
| `KnowledgeParseResultServiceImplTest`（8） | 分类异常、SSE 过滤三分支、幂等、不写业务表 |
| `KnowledgeParseStuckScannerTest`（8） | 终态补推/仍created告警/未超阈值不动/非当前任务跳过/按dataset阈值（参数化）/不写DB |
| `ParseResultKafkaConfigTest`（3） | ErrorHandler 构建、异常分类、recover 不抛出+计数 |
| `ParseResultConsumerEmbeddedKafkaTest`（2） | 坏消息不阻塞后续、缓存补偿隔离（@EmbeddedKafka） |

## 2. 与 TD 的偏离（及原因）

1. **依赖位置**：TD §3.1 写 `link-api/pom.xml` 引入 actuator + spring-kafka-test；实际放在 `link-service/pom.xml`。原因：`ParseResultMetrics` 与 `ParseResultConsumerEmbeddedKafkaTest` 都在 link-service，依赖必须落在引用模块；link-api 依赖 link-service 故 actuator 仍向上传递、效果等价且更正确。
2. **集成测试命名**：TD 命名 `ParseResultConsumerIT`；本项目未配置 maven-failsafe，`*IT` 不会被 Surefire 执行。改名为 `ParseResultConsumerEmbeddedKafkaTest`（`*Test`）以纳入 `mvn test`。
3. 其余实现级细节（退避具体参数、扫描去重、阈值配置载体、fail-open）均按 TD/brief 既定方向落地。

## 3. 验证结果

- `mvn test`：link-service 77、link-api 98，全模块 BUILD SUCCESS，0 失败。
- `python3 scripts/check_docs_sync.py --working`：30 changed file(s)，no doc-sync issues。
- `python3 scripts/check_ai_links.py`：AI links OK。

## 4. Scenario 覆盖

acceptance.feature 17 个 Scenario 全部由上述测试承接（映射见 technical_design.md §10.2）。

## 5. 质量审查

`code-review-and-quality` 审查结论 **APPROVE**（修复 R1 后）。

- **R1（Required，已修复）**：`ParseResultKafkaConfig` 删除 `setCommitRecovered(true)`——该选项要求容器 `AckMode.MANUAL_IMMEDIATE`，而专用工厂沿用默认 `BATCH`，原设置是误导性无效项。删除后依赖默认行为（recover 后位移随 BATCH 提交推进），坏消息仍正确跳过，EmbeddedKafka 集成测试复测通过。
- **Suggestion（已记录、未阻断）**：S1 以 DB 补推仅命中竞态窗口（与 brief §3.3 范围限制一致，靠前端轮询自愈）；S2 基础设施重试-耗尽缺行为级 IT；S3 扫描在解析表缺失环境的顶层查询保护；S4 扫描对空指针候选静默跳过的注释。可作为后续增强。

复测：`mvn test` link-service 77 / link-api 98 全绿，doc-sync 0 问题，ai-links OK。
