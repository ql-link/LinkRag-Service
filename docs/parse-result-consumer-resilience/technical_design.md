# parse_result 消费接收兜底 技术设计

- **文档状态：** 技术方案已审核冻结（2026-05-28）
- **项目名称：** toLink-Service
- **业务域：** 知识文件解析 / MQ 消费
- **需求名称：** parse-result-consumer-resilience
- **业务输入：** docs/parse-result-consumer-resilience/brief.md
- **验收输入：** docs/parse-result-consumer-resilience/acceptance.feature
- **输出文件：** docs/parse-result-consumer-resilience/technical_design.md
- **最后更新时间：** 2026-05-28

---

## 1. 文档修订记录

| 版本号 | 修改日期 | 修改内容简述 | 来源/提出人 | 审核状态 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-05-28 | 初始技术设计创建 | brief.md + acceptance.feature | 待审核 |
| v1.1 | 2026-05-28 | 锁定决策：引入 actuator(Micrometer) 与 spring-kafka-test；退避默认 3 次/1s/x2/10s | 用户确认 | 已冻结 |

---

## 2. 输入依据与设计目标

### 2.1 输入依据映射

| 输入来源 | 关键结论 | 技术设计承接方式 |
| :--- | :--- | :--- |
| `brief.md` §0 | Python 是权威记账人（先写 `document_parsed_log.task_status` 再发 MQ），Java 只校验 + 转发 SSE，从不回写业务状态 | 所有改动只读 DB + 转发/补推 SSE + 告警，不新增任何对 `document_parsed_log`/`document_parse_file`/`document_original_file` 的写 |
| `brief.md` §3.1 | parse_result 单开专用容器工厂 + 错误处理器；失败三分类；带退避重试；无 DLQ；告警=日志+指标；不引入手动 ACK | 新增 `ParseResultKafkaConfig`，自定义 `SeekToCurrentErrorHandler` + 退避 + recoverer；`handleParseResult` 改抛分类异常 |
| `brief.md` §3.2 | 转发前比对 `taskId == latestParseTaskId`，不等不推、空指针 fail-open | 在 `handleParseResult` 内做当前任务过滤，`publishResultEvent` 保持“只推”职责 |
| `brief.md` §3.3 | 定时扫描 `created` 超数据集阈值的当前任务；DB 终态补推、仍 created 告警；阈值默认 5min 按 dataset 覆盖 | 新增 `KnowledgeParseStuckScanner` + `@EnableScheduling` + `ParseResultStuckProperties` |
| `acceptance.feature` | 17 个 Scenario（ACK 8 / SSE 3 / 卡住 5 / 不变量 1） | 见 §7 方法级变更总表与 §10 Scenario 覆盖自检 |

### 2.2 技术目标

- parse_result 消费侧具备失败分类：业务不可恢复消息不空转、瞬时故障带退避重试、均不静默丢弃（告警日志 + 监控指标）。
- 改动隔离在 parse_result 专用容器工厂，缓存补偿（`tolink.cache.evict`）消费链路零影响。
- SSE 只为当前任务（`latest_parse_task_id`）推送终态，旧任务/乱序不误导前端。
- 新增卡住扫描，以 DB 为权威源补推或告警，作为通知丢失的观测兜底。
- **非目标**：不改 Python 端、不改 MQ 消息体、不引入 DLQ、不引入手动 ACK、不在 Java 侧回写任何解析业务状态、不做解析自动重试。

---

## 3. 改动范围

### 3.1 改动文件目录树

```text
toLink-Service/
├── link-service/
│   └── src/main/java/com/qingluo/link/service/
│       ├── mq/
│       │   ├── KnowledgeParseResultKafkaReceiver.java        # [修改] @KafkaListener 指向专用容器工厂
│       │   └── config/
│       │       └── ParseResultKafkaConfig.java               # [新增] 专用容器工厂 + 错误处理器 + recoverer
│       ├── impl/know/
│       │   ├── KnowledgeParseResultServiceImpl.java          # [修改] 失败分类异常 + SSE 当前任务过滤
│       │   └── KnowledgeParseStuckScanner.java               # [新增] 卡住扫描定时任务
│       ├── config/
│       │   ├── ParseResultStuckProperties.java               # [新增] 阈值/扫描间隔/按 dataset 覆盖配置
│       │   └── SchedulingConfig.java                         # [新增] @EnableScheduling
│       └── support/
│           └── ParseResultMetrics.java                       # [新增] 监控指标封装（Micrometer 可选）
├── link-service/src/main/java/com/qingluo/link/service/exception/
│   ├── NonRetryableParseResultException.java                 # [新增] 不可重试业务异常
│   └── ParseResultPendingException.java                      # [新增] 暧昧/待重试异常（log 未就绪）
├── link-api/src/main/resources/
│   ├── application-dev.yml                                   # [修改] tolink.parse-result.stuck.* 配置
│   └── application-local.yml                                 # [修改] 同上
└── link-api/pom.xml                                          # [修改] 引入 actuator（指标）与 spring-kafka-test（集成测试）
```

### 3.2 文件级改动说明

| 文件 | 动作 | 改动目的 | 是否必须 |
| :--- | :--- | :--- | :--- |
| `ParseResultKafkaConfig.java` | 新增 | 专用 `ConcurrentKafkaListenerContainerFactory` + `SeekToCurrentErrorHandler`（退避 + notRetryable 分类 + recoverer） | 是 |
| `KnowledgeParseResultKafkaReceiver.java` | 修改 | `@KafkaListener` 增加 `containerFactory="parseResultKafkaListenerContainerFactory"` | 是 |
| `KnowledgeParseResultServiceImpl.java` | 修改 | 失败抛分类异常；转发前做当前任务过滤 | 是 |
| `NonRetryableParseResultException.java` | 新增 | 标识不可重试（不匹配类）异常 | 是 |
| `ParseResultPendingException.java` | 新增 | 标识 log 未就绪（可重试）异常 | 是 |
| `KnowledgeParseStuckScanner.java` | 新增 | 定时扫描卡住任务，DB 终态补推 / 仍 created 告警 | 是 |
| `ParseResultStuckProperties.java` | 新增 | 阈值（默认 5min）、扫描间隔、按 dataset 覆盖 | 是 |
| `SchedulingConfig.java` | 新增 | 开启 `@EnableScheduling`（当前项目无调度基础设施） | 是 |
| `ParseResultMetrics.java` | 新增 | 封装监控指标计数，Micrometer 缺失时降级为仅日志 | 是 |
| `application-dev.yml` / `application-local.yml` | 修改 | 写入 stuck 配置默认值 | 是 |
| `link-api/pom.xml` | 修改 | 引入 `spring-boot-starter-actuator`（Micrometer）与 `spring-kafka-test`（test 域） | 待确认（见 §12） |
| `CacheCompensationKafkaReceiver.java` | 不改 | 验证隔离：仍用默认容器工厂 | 是（仅作回归对象） |

---

## 4. 当前系统分析

| 类型 | 文件/类/方法 | 当前行为 | 问题或复用点 |
| :--- | :--- | :--- | :--- |
| 消费入口 | `KnowledgeParseResultKafkaReceiver.receive(String)` | `@KafkaListener` 未指定 containerFactory，用 Boot 默认；调用 `parseMsg` 反序列化后转 `handleParseResult` | 需指向专用工厂；`parseMsg` 内 `validate` 抛 `IllegalArgumentException`（坏 JSON）也会进默认重试 |
| 业务校验 | `KnowledgeParseResultServiceImpl.handleParseResult(MsgPayload)` | 读 `document_parsed_log` 校验 taskId/状态/归属，全部失败抛 `BusinessException`；通过则 `publishResultEvent`。**已加载 `parseFile`**（含 `latestParseTaskId`） | 复用已加载的 `parseFile` 做当前任务过滤；改抛分类异常 |
| SSE | `KnowledgeParseSseServiceImpl.publishResultEvent(MsgPayload)` | 按 `original_file_id` 找文件并向其 emitters 推送，无当前任务校验 | 保持“只推”，过滤前移到调用方；扫描器复用此方法补推 |
| 消息模型 | `KnowledgeParseResultMQ`（字段 task_id/original_file_id/document_parsed_log_id/dataset_id/user_id/task_status/failure_reason/parse_finished_at） | `parseMsg` 反序列化 + `validate` | 不变；scanner 需用 DB 数据装配同结构 `MsgPayload` 复用 `publishResultEvent` |
| 实体 | `KnowledgeParsedLog`（`document_parsed_log`，含 task_status/created_at/documentParseFileId/documentOriginalFileId） | Python 创建并推进，Java 只读 | scanner 查询/重读对象；`task_status ∈ {created,success,failed}` |
| 实体 | `KnowledgeParseFile`（`document_parse_file`，含 latestParseTaskId/datasetId/userId/documentOriginalFileId） | 发起任务时写 latestParseTaskId | 当前任务指针来源；scanner 与过滤的锚点 |
| Mapper | `KnowledgeParsedLogMapper` / `KnowledgeParseFileMapper`（裸 `BaseMapper`） | 仅继承 MP 基础方法 | 用 `LambdaQueryWrapper` 查询，无需新增 XML |
| MQ 自动配置 | `KafkaMQAutoConfiguration` | 只配生产端（MQSend、建 topic），**无监听容器工厂** | 监听走 Boot 默认 `kafkaListenerContainerFactory`；新增专用工厂不影响这里 |
| 共用消费者 | `CacheCompensationKafkaReceiver`（`tolink.cache.evict`） | 同样用 Boot 默认工厂 | 必须保持不动以验证隔离 |
| 调度 | 全工程 | **无 `@EnableScheduling` / `@Scheduled`** | 需新增调度开关 |
| 指标 | 全工程 | **无 Micrometer / Actuator** | 需引入或降级为日志计数 |
| spring-kafka | 2.7.4（Boot 2.5.3 托管） | 默认 `SeekToCurrentErrorHandler`+`FixedBackOff(0L,9)`、ackMode BATCH、`enable.auto.commit=false` | 2.7 支持 `addNotRetryableExceptions` 与 `ExponentialBackOffWithMaxRetries` |

---

## 5. 总体方案设计

### 5.1 总体流程

```mermaid
flowchart TD
    K["parse_result 消息到达\n(专用容器工厂)"] --> P["parseMsg 反序列化+validate"]
    P -->|坏 JSON IllegalArgumentException| RC["recoverer: 告警日志+指标, 提交跳过"]
    P --> H["handleParseResult"]
    H --> V{"校验 log/taskId/状态/归属"}
    V -->|不匹配 → NonRetryableParseResultException| RC
    V -->|log 不存在 → ParseResultPendingException| RT["退避重试有限次"]
    V -->|读库异常 → DataAccessException| RT
    RT -->|重试中通过| F
    RT -->|耗尽| RC
    V -->|通过| F{"taskId == latestParseTaskId?"}
    F -->|相等| PUB["publishResultEvent 推终态 SSE"]
    F -->|不等(旧任务)| AUD["记审计日志, 不推, 提交"]
    F -->|指针为空| PUB2["fail-open 推送 + 审计日志"]

    S["定时扫描(SchedulingConfig)"] --> SQ["查 created 超数据集阈值的当前任务"]
    SQ --> RR["按 task_id 重读 document_parsed_log"]
    RR -->|终态| SF{"仍是当前任务?"}
    SF -->|是| PUB
    RR -->|仍 created| AL["告警日志+指标, 不补推"]
```

### 5.2 模块边界

| 模块 | 职责 | 本次是否改动 |
| :--- | :--- | :--- |
| link-service / mq | parse_result 容器工厂、错误处理、消费入口 | 是 |
| link-service / impl.know | 校验分类、SSE 当前任务过滤、卡住扫描 | 是 |
| link-service / config | 调度开关、stuck 配置 | 是（新增） |
| link-components / mq | 通用 MQ 框架、生产端、缓存补偿 | 否 |
| link-model | 实体 | 否（只读，无字段变更） |
| Python 端 | 写终态、发消息 | 否 |

---

## 6. API、消息与数据设计

### 6.1 API 设计

无 HTTP 接口新增或变更。SSE 订阅接口（`subscribe`）契约不变。

### 6.2 MQ 消息设计

- `tolink.rag.parse_result` 消息体**不变**（不加 `previous_task_id` 等字段）。
- 消费侧改动：专用容器工厂 + 错误处理策略。无生产端改动。
- 无 DLQ topic 新增。
- 契约检查（contract-guard 思路）：未改 `KnowledgeParseResultMQ` 字段与 topic 常量，`docs/reference/mq_contracts.md` 无需改字段；但需补充“parse_result 消费错误处理策略”说明段（见 §11 文档同步）。

### 6.3 数据与存储设计

- 无表结构变更，无 Entity 字段新增，无迁移脚本。
- 仅新增对 `document_parse_file` / `document_parsed_log` 的**只读**查询（`LambdaQueryWrapper`），无写操作。
- 配置项（非数据库）：`tolink.parse-result.stuck.default-threshold`（Duration，默认 `5m`）、`scan-interval`（默认 `1m`）、`dataset-thresholds`（Map<Long,Duration>）；`tolink.parse-result.retry.max-attempts` / `initial-interval` / `multiplier` / `max-interval`。

---

## 7. 方法级实现方案

### 7.1 方法级变更总表

| 文件 | 类/对象 | 方法/成员 | 动作 | 入参变化 | 返回变化 | 改动目的 | 对应 Scenario |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| ParseResultKafkaConfig | ParseResultKafkaConfig | `parseResultKafkaListenerContainerFactory(ConsumerFactory, ...)` | 新增 | - | `ConcurrentKafkaListenerContainerFactory<String,String>` | 专用工厂 + 错误处理器 | 坏消息不阻塞后续正常消息；缓存补偿不受影响；基础设施异常带退避重试耗尽后跳过 |
| ParseResultKafkaConfig | ParseResultKafkaConfig | `parseResultErrorHandler(ParseResultMetrics)` | 新增 | - | `SeekToCurrentErrorHandler` | 退避 + notRetryable 分类 + recoverer | 归属不匹配不重试；log 暂无重试；基础设施重试耗尽 |
| ParseResultKafkaConfig | (recoverer lambda) | `recover(record, ex)` | 新增 | - | void | 告警日志 + 指标 + 提交跳过 | 各 recover 分支；不引入 DLQ |
| KnowledgeParseResultKafkaReceiver | KnowledgeParseResultKafkaReceiver | `receive(String)` | 修改 | 注解加 containerFactory | 不变 | 指向专用工厂 | 缓存补偿不受影响 |
| KnowledgeParseResultServiceImpl | KnowledgeParseResultServiceImpl | `handleParseResult(MsgPayload)` | 修改 | 不变 | 不变 | 改抛分类异常 + 当前任务过滤 | 归属/状态不匹配；log 暂无；幂等；SSE 过滤三场景；不变量 |
| NonRetryableParseResultException | - | (class) | 新增 | - | - | 标识不可重试 | 归属/状态不匹配 |
| ParseResultPendingException | - | (class) | 新增 | - | - | 标识 log 未就绪可重试 | log 暂不存在重试 |
| KnowledgeParseStuckScanner | KnowledgeParseStuckScanner | `scan()` | 新增 | - | void | 扫描卡住任务并补推/告警 | 卡住扫描全部 5 个 + 不变量 |
| KnowledgeParseStuckScanner | KnowledgeParseStuckScanner | `thresholdOf(datasetId)` | 新增 | datasetId | Duration | 按 dataset 取阈值缺省回落 | 阈值按数据集生效 |
| ParseResultStuckProperties | ParseResultStuckProperties | getters/setters | 新增 | - | - | 配置载体 | 阈值按数据集生效 |
| ParseResultMetrics | ParseResultMetrics | `recordRecover(reason)` / `recordStuck()` / `recordRepushed()` | 新增 | - | void | 指标封装，Micrometer 可选 | 告警+指标相关场景 |
| SchedulingConfig | SchedulingConfig | (class `@EnableScheduling`) | 新增 | - | - | 开启调度 | 卡住扫描运行 |

### 7.2 逐方法实现设计

#### 7.2.1 `ParseResultKafkaConfig.parseResultKafkaListenerContainerFactory(...)`

- 修改后职责：构建仅供 parse_result 使用的监听容器工厂，注入自定义错误处理器。
- 详细步骤：
  1. 注入 Boot 自动装配的 `ConsumerFactory<Object,Object>`（或 `ConsumerFactory<String,String>`）。
  2. `new ConcurrentKafkaListenerContainerFactory<String,String>()`，`setConsumerFactory(...)`。
  3. `setErrorHandler(parseResultErrorHandler(...))`（2.7 用容器级 `ErrorHandler`/`SeekToCurrentErrorHandler`）。
  4. Bean 名固定 `parseResultKafkaListenerContainerFactory`。
- 并发边界：保持单分区单线程语义（topic 当前 1 分区，见 `kafka-topic-partitions: 1`）。
- 调用关系：被 `KnowledgeParseResultKafkaReceiver.@KafkaListener` 引用。
- 对应测试：`ParseResultKafkaConfigTest`（断言工厂含自定义 ErrorHandler、notRetryable 集合、退避参数）；EmbeddedKafka 集成测试（坏消息不阻塞后续）。

#### 7.2.2 `ParseResultKafkaConfig.parseResultErrorHandler(...)`

- 职责：定义重试与分类策略。
- 详细步骤：
  1. recoverer = `(record, ex) -> metrics + 告警日志`（根据 `ex` 类型选 reason；不抛出 = 提交跳过）。
  2. backOff = `ExponentialBackOffWithMaxRetries(maxAttempts)`，`initialInterval`/`multiplier`/`maxInterval` 从配置读；**建议默认**：max-attempts=3、initial=1s、multiplier=2.0、max=10s（替换零退避 `FixedBackOff(0,9)`）。
  3. `new SeekToCurrentErrorHandler(recoverer, backOff)`。
  4. `addNotRetryableExceptions(NonRetryableParseResultException.class, IllegalArgumentException.class, org.springframework.kafka.support.serializer.DeserializationException.class)` → 这些立即 recover、不重试。
  5. `ParseResultPendingException` 与 `DataAccessException` **不在** notRetryable 集合 → 走退避重试。
- 异常边界：recoverer 永不抛出，确保提交跳过、不进入无限循环。
- 对应测试：`ParseResultKafkaConfigTest` + recoverer 单测（验证日志/指标、不抛出）。

#### 7.2.3 `KnowledgeParseResultServiceImpl.handleParseResult(MsgPayload)`（修改）

- 当前行为：四类校验失败抛 `BusinessException`，通过则 `publishResultEvent`。
- 修改后步骤：
  1. `logRecord = selectById(documentParsedLogId)`；为 `null` → 抛 `ParseResultPendingException`（可重试，覆盖跨库可见性/主从延迟）。
  2. `taskId` 不等 → 抛 `NonRetryableParseResultException`。
  3. `taskStatus` 不等 → 抛 `NonRetryableParseResultException`。
  4. 归属字段（originalFileId/datasetId/userId 与 parseFile、originalFile 交叉）不一致 → 抛 `NonRetryableParseResultException`。
  5. **当前任务过滤**（新增）：取已加载的 `parseFile.getLatestParseTaskId()`：
     - 为空（`!StringUtils.hasText`）→ fail-open：记审计日志（“指针缺失，放行”）后 `publishResultEvent(payload)`。
     - 等于 `payload.getTaskId()` → `publishResultEvent(payload)`。
     - 不等 → 记审计日志（“旧任务结果忽略，taskId=…, current=…”），**不推送**，正常返回（提交）。
  6. 读库异常（`DataAccessException` 等）自然向上抛 → 走退避重试。
- 事务边界：纯读 + 转发，无事务写。
- 幂等与并发边界：重跑只重复校验 + 重复 SSE 推送，无副作用（满足“重试是幂等的”）。
- 对应测试：`KnowledgeParseResultServiceImplTest`（扩充：分类异常类型、SSE 过滤三分支、不写 DB）。

#### 7.2.4 `KnowledgeParseStuckScanner.scan()`（新增，`@Scheduled`）

- 职责：发现卡住任务，DB 终态补推、仍 created 告警。
- 详细步骤：
  1. `@Scheduled(fixedDelayString = "${tolink.parse-result.stuck.scan-interval-ms:60000}")`。
  2. 粗筛：`KnowledgeParsedLogMapper.selectList(created 且 created_at < now - 最小阈值)`（最小阈值=默认与各 dataset 覆盖中的最小值，降低扫描量）。
  3. 对每条：
     - 取 `parseFile = parseFileMapper.selectById(log.getDocumentParseFileId())`；
     - **当前任务校验**：`log.getTaskId().equals(parseFile.getLatestParseTaskId())`，否则跳过（非当前任务不处理）；
     - **精确阈值校验**：`thresholdOf(parseFile.getDatasetId())`，未超则跳过；
     - **重读权威状态**：`fresh = parsedLogMapper.selectById(log.getId())`：
       - `success`/`failed` → 用 fresh + parseFile 装配 `MsgPayload`，再次确认仍是当前任务后 `publishResultEvent` 补推（幂等）；`metrics.recordRepushed()`。
       - 仍 `created` → 告警日志（含 task_id/original_file_id/document_parsed_log_id/dataset_id/created_at/超时时长/环境）+ `metrics.recordStuck()`，不补推。
  4. 单次扫描异常需 try/catch 包裹每条，避免一条异常中断整批。
- 并发边界：单实例调度；多实例部署下重复补推幂等、重复告警用 gauge/限频缓解（见 §9）。
- 对应测试：`KnowledgeParseStuckScannerTest`（Mockito：终态补推 / 仍 created 告警 / 未超阈值不动 / 非当前任务跳过 / 按 dataset 阈值 Outline / 不写 DB）。

#### 7.2.5 `KnowledgeParseStuckScanner.thresholdOf(Long datasetId)`

- 返回 `properties.getDatasetThresholds().getOrDefault(datasetId, properties.getDefaultThreshold())`，默认 `Duration.ofMinutes(5)`。

#### 7.2.6 `ParseResultMetrics`（新增）

- 封装 `MeterRegistry`（通过 `ObjectProvider<MeterRegistry>` 注入，缺失时为 no-op，仅打日志），暴露 `recordRecover(String reason)`、`recordStuck()`、`recordRepushed()`。
- 指标名建议：`tolink.parse_result.recover`（tag reason=non_retryable/pending_exhausted/infra_exhausted/bad_payload）、`tolink.parse_result.stuck`、`tolink.parse_result.repushed`。

---

## 8. 组件与集成设计

- **调度**：新增 `SchedulingConfig`（`@Configuration @EnableScheduling`），位于 `com.qingluo.link.service.config`，被现有 ComponentScan 覆盖。
- **指标**：引入 `spring-boot-starter-actuator`（带 Micrometer）；不暴露外部平台，仅本地 registry/可选 Prometheus endpoint。若不引依赖，`ParseResultMetrics` 降级为日志计数（保证可编译、可测）。
- **Kafka 工厂**：复用 Boot 自动装配的 `ConsumerFactory`，只覆盖容器工厂与错误处理器，不动消费组/反序列化配置。

---

## 9. 异常处理与降级策略

| 异常场景 | 处理方式 | 是否抛出 | 是否影响消息确认 |
| :--- | :--- | :--- | :--- |
| 坏 JSON（`parseMsg.validate`） | recoverer 告警+指标 | IllegalArgumentException（notRetryable） | 立即提交跳过 |
| taskId/状态/归属不匹配 | recoverer 告警+指标 | NonRetryableParseResultException | 立即提交跳过 |
| log 不存在（暧昧） | 退避重试，耗尽 recover | ParseResultPendingException（retryable） | 重试后提交跳过 |
| 读库失败（基础设施） | 退避重试，耗尽 recover | DataAccessException（retryable） | 重试后提交跳过 |
| 旧任务/乱序（当前任务过滤未命中） | 记审计日志、不推送 | 不抛出 | 正常提交 |
| scanner 单条处理异常 | try/catch 单条隔离 + 告警 | 不向调度抛出 | 不适用（非消费） |
| 重复告警噪声 | mode-1 用 gauge/限频；补推幂等 | - | - |

---

## 10. 测试方案

### 10.1 方法级测试映射

| 被测文件/方法 | 测试文件 | 对应 Scenario | 断言要点 |
| :--- | :--- | :--- | :--- |
| `handleParseResult` 分类 | `KnowledgeParseResultServiceImplTest` | 归属不匹配不重试并跳过；终态不一致一律不可恢复（Outline） | 抛 `NonRetryableParseResultException`；未调用 `publishResultEvent`；未调 `updateById` |
| `handleParseResult` log 暂无 | `KnowledgeParseResultServiceImplTest` | 日志暂不存在带退避重试且最终跳过；窗口内出现则正常处理 | 抛 `ParseResultPendingException`；第二次 selectById 返回则 publish |
| `handleParseResult` 幂等 | `KnowledgeParseResultServiceImplTest` | 重试是幂等的 | 多次调用仅多次 publish，无 `updateById` |
| `handleParseResult` SSE 过滤 | `KnowledgeParseResultServiceImplTest` | 当前任务推送 / 旧任务不推 / 指针为空 fail-open | publish 调用与否；审计日志（用 LogCaptor/Appender 或行为断言） |
| `ParseResultKafkaConfig` | `ParseResultKafkaConfigTest` | 基础设施重试耗尽跳过；坏消息不阻塞后续 | ErrorHandler 含退避与 notRetryable 集合 |
| 消费隔离 + 不阻塞 | `ParseResultConsumerIT`（@EmbeddedKafka） | 坏消息不阻塞后续；缓存补偿不受影响 | 投递坏+好消息，好消息被处理；cache.evict 正常 |
| `KnowledgeParseStuckScanner.scan` | `KnowledgeParseStuckScannerTest` | 卡住扫描 5 场景 + 不变量 | 终态→publish；created→告警/指标；未超阈值→无动作；非当前任务→跳过；阈值 Outline；无 `updateById` |
| recoverer | `ParseResultMetricsTest` / recoverer 单测 | 告警内容可回查 DB；不引入 DLQ | 日志含 task_id 等键；recoverer 不抛出 |

### 10.2 Scenario 覆盖自检

| Scenario | 承接方法 | 承接测试 | 是否覆盖 |
| :--- | :--- | :--- | :--- |
| 归属不匹配的消息不重试并跳过 | handleParseResult + errorHandler | ServiceImplTest + ConfigTest | ✅ |
| 消息与已持久化终态不一致一律判为不可恢复（Outline） | handleParseResult | ServiceImplTest | ✅ |
| 坏消息不阻塞后续正常消息 | errorHandler + recoverer | ParseResultConsumerIT | ✅ |
| 日志暂不存在时带退避重试且最终跳过 | handleParseResult + errorHandler | ServiceImplTest + ConfigTest | ✅ |
| 日志在重试窗口内出现则正常处理 | handleParseResult | ServiceImplTest（两次 selectById 桩） | ✅ |
| 基础设施异常带退避重试，耗尽后告警跳过 | errorHandler | ConfigTest + ConsumerIT | ✅ |
| 重试是幂等的 | handleParseResult | ServiceImplTest | ✅ |
| 缓存补偿消费链路不受本次改动影响 | receiver containerFactory 隔离 | ConsumerIT（cache.evict 仍默认工厂） | ✅ |
| 当前任务的终态正常推送 | handleParseResult 过滤 | ServiceImplTest | ✅ |
| 旧任务迟到结果不推终态且记审计 | handleParseResult 过滤 | ServiceImplTest | ✅ |
| 当前任务指针缺失时放行并记审计 | handleParseResult 过滤 | ServiceImplTest | ✅ |
| 未超阈值的进行中任务不告警不补推 | scan + thresholdOf | ScannerTest | ✅ |
| 超阈值且 DB 已终态则以 DB 为准补推 | scan | ScannerTest | ✅ |
| 超阈值且 DB 仍为 created 则只告警不补推 | scan | ScannerTest | ✅ |
| 告警内容可回查 DB | scan 告警 | ScannerTest（日志断言） | ✅ |
| 超时阈值按数据集生效，缺省回落默认 5 分钟（Outline） | thresholdOf | ScannerTest（Outline） | ✅ |
| Java 在任何分支都不回写业务终态 | 全部 | ServiceImplTest + ScannerTest（verify never updateById） | ✅ |

### 10.3 回归命令

```bash
mvn -pl link-service test
mvn -pl link-api test
mvn test
python3 scripts/check_docs_sync.py --working
```

---

## 11. 发布与回滚

- **发布**：纯 Java 消费侧 + 配置 + 新增定时任务，无 DB 迁移、无 MQ 契约变更，可随服务滚动发布。
- **配置**：`tolink.parse-result.stuck.*` 与 `tolink.parse-result.retry.*` 提供默认值，缺省可运行。
- **回滚**：移除/回退专用容器工厂引用（`@KafkaListener` 去掉 containerFactory 即回到默认）、关闭 `@EnableScheduling` 即停扫描；无数据残留需清理。
- **文档同步**：更新 `docs/reference/mq_contracts.md`（新增 parse_result 消费错误处理与卡住兜底说明）、`docs/architecture/mq_module.md`（专用容器工厂与隔离）、`docs/guides/configuration.md`（新增配置项）。

---

## 12. 风险与待确认问题

| 风险/问题 | 影响 | 建议处理 |
| :--- | :--- | :--- |
| ~~引入 `spring-boot-starter-actuator` 作为指标载体~~ **已定：引入** | 新增依赖 | Micrometer 本地 registry，不接外部平台；`ParseResultMetrics` 经 `ObjectProvider<MeterRegistry>` 注入保证降级安全 |
| ~~引入 `spring-kafka-test`（EmbeddedKafka）做集成测试~~ **已定：引入（test 域）** | 新增 test 依赖 | 覆盖“不阻塞后续/缓存补偿隔离”集成场景 |
| ~~退避默认参数~~ **已定** | - | max-attempts=3、initial=1s、multiplier=2.0、max=10s |
| `@EnableScheduling` 首次引入 | 全局开启调度 | 仅本需求一个 `@Scheduled`；多实例需注意重复执行（补推幂等、告警限频缓解） |
| scanner 粗筛扫描量 | created 记录多时查询压力 | 用最小阈值 + created 过滤 + 当前任务校验收敛；必要时加分页/上限 |
| `ConsumerFactory` 泛型注入 | Boot bean 为 `ConsumerFactory<Object,Object>` | 注入后构造 `ConcurrentKafkaListenerContainerFactory<String,String>`，必要时用原始类型/适配 |
| 多实例下卡住告警重复 | 告警噪声 | 用 gauge 指标 + 日志限频；本需求不引入分布式锁 |

---

## 13. 实施顺序

1. 新增异常类 `NonRetryableParseResultException`、`ParseResultPendingException`。
2. 改 `handleParseResult`：分类异常 + 当前任务过滤；补充/调整 `KnowledgeParseResultServiceImplTest`。
3. 新增 `ParseResultMetrics`（含 Micrometer 可选降级）。
4. 新增 `ParseResultKafkaConfig`（容器工厂 + 错误处理器 + recoverer）；`KnowledgeParseResultKafkaReceiver` 指向专用工厂。
5. 新增 `ParseResultStuckProperties`、`SchedulingConfig`、`KnowledgeParseStuckScanner` + `KnowledgeParseStuckScannerTest`。
6. 写 `application-*.yml` 配置默认值。
7. （按 §12 决策）引入 actuator / spring-kafka-test 并补 `ParseResultKafkaConfigTest`、`ParseResultConsumerIT`。
8. 跑 `mvn -pl link-service test` → `mvn test` → 文档同步校验。

---

## 14. 人工审核清单

- [x] 改动文件目录树已确认
- [x] 方法级变更总表已确认
- [x] 消息 / 数据 / 事务边界已确认（确认无 DB 写、无 MQ 契约变更）
- [x] §12 两项依赖决策已拍板（actuator / spring-kafka-test：均引入）
- [x] 退避默认参数（3 次 / 1s / x2 / 10s）已确认
- [x] 测试方案已确认
