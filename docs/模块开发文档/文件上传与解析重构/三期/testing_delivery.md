# ToLink Service 文件上传与解析重构三期测试执行与交付记录

> **文档状态：** 已完成
> **项目名称**：ToLink Service
> **模块名称**：文件上传与解析重构
> **当前期次**：三期
> **需求文档**：`docs/模块开发文档/文件上传与解析重构/三期/requirement.md`
> **技术文档**：`docs/模块开发文档/文件上传与解析重构/三期/technical_design.md`
> **改造报告**：`docs/模块开发文档/文件上传与解析重构/三期/implementation_report.md`
> **分支名称**：`refactor/update-file-upload-parse`
> **执行人：** AI
> **最后更新时间：** 2026-04-30

---

## 1. 使用说明

本文件用于记录三期“`parse_result` MQ 终态回传”改造的实际测试执行结果、遗留风险和当前交付结论。

---

## 2. 测试范围与目标

### 2.1 本次要验证的内容

- `parse_result` 扁平 JSON 消息模型序列化与字段校验
- Kafka 结果消费者按新契约消费 `tolink.rag.parse_result`
- Java 结果消费服务校验 `document_parse_log_id + task_id + 文件归属` 后转发 SSE
- 内部回调接口只接受 `processing/progress`
- 现有文件解析提交和结果查询接口未被本次改造破坏

### 2.2 本次不验证的内容

- Python 实际发送 `parse_result` 的联调结果
- Kafka broker 可用时的端到端真实投递
- `parse_task` 更强发送确认、Outbox、本地消息表
- 悬空解析任务失败化治理
- 多实例 SSE 广播

### 2.3 验收项映射

| 验收项 | 对应用例编号 | 是否覆盖 | 备注 |
| :--- | :--- | :--- | :--- |
| 终态结果回传 | TC-01, TC-02, TC-03 | 是 | 单测 + 集成装配验证 |
| 回调职责收缩 | TC-04, TC-E01, TC-E02 | 是 | Controller 测试 |
| 结果查询与提交不回归 | TC-05, TC-06 | 是 | 复用现有 Controller 测试 |

---

## 3. 测试前提与环境准备

### 3.1 环境信息

| 项目 | 内容 |
| :--- | :--- |
| 分支 | `refactor/update-file-upload-parse` |
| 部署环境 | 本地 Maven 测试环境 |
| 服务状态 | `link-service`、`link-api` 测试上下文正常启动 |
| 外部依赖 | H2、MockMvc、Mockito；Kafka 仅验证消费者装配，不依赖真实 broker 成功连接 |
| 相关配置 | `qingluopay.mq.vender=kafka`、`spring.kafka.listener.auto-startup=false`、`tolink.knowledge-file.service-token=test-service-token` |

### 3.2 测试前提

- 已完成三期代码实现。
- 已完成三期技术文档、改造报告和公共契约回写。
- 本地 Maven 环境可执行 `link-service` 与 `link-api` 测试。

### 3.3 测试数据准备

| 数据项 | 用途 | 准备方式 | 备注 |
| :--- | :--- | :--- | :--- |
| `task_id`、`document_parse_log_id`、`original_file_id` | 验证 `parse_result` 消息模型与归属校验 | 单测中构造 | 不依赖真实 Python |
| H2 测试库中的 `document_parse_log` / `document_original_file` 数据 | 验证结果消费服务和内部回调 | `KnowledgeFileControllerTest`/Service Test 插入 | 自动清理 |
| `test-service-token` | 验证内部回调鉴权 | SpringBootTest properties 注入 | 仅测试环境使用 |

### 3.4 执行方式

- 单元测试
- 集成测试
- Controller 接口测试
- 日志检查
- 数据库检查

---

## 4. 测试执行清单

### 4.1 主流程测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-01 | `parse_result` 扁平 JSON 序列化 | 无 | 执行 `KnowledgeParseResultMQTest` | 输出 JSON 含 `task_id`、`document_parse_log_id`、`parse_finished_at` 等新字段，且无 `payload` 包裹 | 通过，消息模型按三期契约序列化 | 通过 | AI |
| TC-02 | Kafka Receiver 按新契约解析结果消息 | 无 | 执行 `KnowledgeParseResultKafkaReceiverTest` | Receiver 能解析扁平 JSON 并把 `MsgPayload` 分发给业务接口 | 通过，日志显示收到 `parse_result` 消息并正确分发 | 通过 | AI |
| TC-03 | 结果消费服务校验后转发 SSE | 构造 `document_parse_log`、`document_original_file` | 执行 `KnowledgeParseResultServiceImplTest` | 成功/失败消息都能通过归属校验并调用 `publishResultEvent` | 通过，成功与失败路径均触发 SSE 转发 | 通过 | AI |
| TC-04 | 内部回调保留 `processing/progress` | 插入一条 `document_parse_log` 记录 | 执行 `KnowledgeFileControllerTest` 中的内部回调用例 | `processing` 回调返回 200 | 通过，返回 `{"code":200,"message":"success","data":null}` | 通过 | AI |
| TC-05 | 手动解析主链路不回归 | 上传成功文件且存在 `document_parsed_file` | 执行 `KnowledgeFileControllerTest.Should_UpdateLatestTaskPointerAndDispatchMq_When_UserSubmitsManualParse` | 仍能更新 `latest_parse_task_id` 并返回 `parsing` | 通过 | 通过 | AI |
| TC-06 | 解析结果查询不回归 | 构造成功/失败解析任务 | 执行 `KnowledgeFileControllerTest.Should_ReturnAllFileParseResults_When_QueryByFileList` | 仍按 `parse_success` / `parse_failed` 返回文件维度结果 | 通过 | 通过 | AI |

### 4.2 异常与边界测试用例

| 用例编号 | 场景 | 前置条件 | 执行步骤 | 预期结果 | 实际结果 | 状态 | 执行人 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| TC-E01 | 内部回调收到终态事件 | 无 | 执行 `KnowledgeFileControllerTest.Should_RejectTerminalCallbackEvent_When_EventTypeIsSuccess` | 返回 400，拒绝 `success/failed` | 通过，返回 `解析回调事件类型仅支持 processing 或 progress` | 通过 | AI |
| TC-E02 | `progress` 事件缺进度值 | 无 | 执行 `KnowledgeFileControllerTest.Should_RejectProgressCallback_When_ProgressMissing` | 返回 400，拒绝空进度 | 通过，返回 `progress 事件必须携带解析进度` | 通过 | AI |
| TC-E03 | `parse_result` 缺少 `document_parse_log_id` | 无 | 执行 `KnowledgeParseResultMQTest.Should_RejectMessage_When_ParseLogIdMissing` | 抛出字段缺失异常 | 通过 | 通过 | AI |
| TC-E04 | `parse_result.task_status` 非 `success/failed` | 无 | 执行 `KnowledgeParseResultMQTest.Should_RejectMessage_When_TaskStatusInvalid` | 抛出状态非法异常 | 通过 | 通过 | AI |
| TC-E05 | 结果消息找不到解析日志 | 无 | 执行 `KnowledgeParseResultServiceImplTest.Should_ThrowBusinessException_When_ParseLogMissing` | 抛 `解析任务不存在` | 通过 | 通过 | AI |
| TC-E06 | 结果消息归属不匹配 | 构造错误 `dataset_id` | 执行 `KnowledgeParseResultServiceImplTest.Should_ThrowBusinessException_When_OwnershipDoesNotMatch` | 抛 `解析结果消息归属信息不匹配` | 通过 | 通过 | AI |

### 4.3 回归检查项

| 检查项 | 检查方式 | 预期结果 | 实际结果 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| 关联旧功能是否受影响 | 执行 `KnowledgeFileControllerTest` 中上传、解析提交、结果查询相关用例 | 原有上传、解析提交、查询逻辑不回归 | 通过 | 通过 |
| 关键接口兼容性 | 验证前端接口路径未变、内部回调路径未变 | 前端接口继续可用，内部回调仅收缩事件类型 | 通过 | 通过 |
| 关键数据读写正确性 | 检查 H2 测试数据与日志断言 | `latest_parse_task_id`、解析结果查询、回调鉴权仍符合预期 | 通过 | 通过 |

---

## 5. 执行证据记录

### 5.1 接口与页面结果

- 内部回调 `processing` 请求返回 200。
- 内部回调 `success` 请求返回 400，符合三期职责收缩预期。
- 结果查询接口继续返回 `FileParseResultDTO` 列表，字段口径未变。

### 5.2 日志与链路记录

- `KnowledgeParseResultKafkaReceiverTest` 运行时输出 `Receive parse result MQ message`。
- `KnowledgeParseResultServiceImplTest` 运行时输出成功/失败终态日志以及归属校验失败日志，符合预期。
- `KnowledgeParseResultIntegrationTest` 运行时可见 Kafka 消费者尝试订阅 `tolink.rag.parse_result`，说明结果消费者已装配生效。

### 5.3 数据库 / 缓存 / MQ / OSS 校验结果

- H2 测试库中 `document_parse_log` / `document_original_file` 数据可支撑三期结果消费校验。
- `KnowledgeFileControllerTest` 继续验证 `document_parsed_file.latest_parse_task_id` 更新与回滚逻辑。
- Kafka 侧本次只验证消费者装配，不以真实 broker 投递成功作为测试结论前提。

---

## 6. 问题记录与处理结果

| 编号 | 问题现象 | 影响范围 | 严重级别 | 当前状态 | 处理结果 / 临时结论 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| BUG-01 | 三期技术文档最初未把所有接口响应格式写全 | 文档联调清晰度 | 中 | 已修复 | 已在 `technical_design.md` 中补齐 `parse`、`parse-results`、SSE、内部回调响应结构 |
| BUG-02 | 三期技术文档初版未在技术文档中放完整 `parse_result` JSON 示例 | 文档完整性 | 低 | 已修复 | 已补完整 JSON 示例 |

---

## 7. 交付结论

### 7.1 测试结论

- 主流程是否通过：通过
- 异常流程是否通过：通过
- 回归检查是否通过：通过

### 7.2 遗留风险

- 尚未完成 Python 真实发送 `parse_result` 的跨仓联调，只完成 Java 侧契约与消费测试。
- `KnowledgeParseResultIntegrationTest` 中 Kafka broker 不可用会产生连接告警，但本次测试目标仅为验证消费者装配，不影响当前结论。
- `parse_task` 更强发送确认和悬空任务失败化治理已明确后置到四期。

### 7.3 是否允许交付

- 是否可交付：有条件可交付
- 交付前提：
  - Python 端按三期契约切换 `parse_result` MQ 发送
  - 联调确认终态结果确实由 MQ 触发前端 SSE
- 联调注意事项：
  - `success/failed` 不应再走内部回调接口
  - `parse_finished_at` 必须带时区
  - `failure_reason` 在 `failed` 时必填
- 发布 / 回滚注意事项：
  - 发布时需要同步 Java/Python 双端契约
  - 若回滚三期，需要恢复终态回调能力或与 Python 侧同步退回

---

## 8. 回写检查

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填测试结论 | 是 | 三期状态已更新 |
| `project_info.md` 已同步更新 | 是 | 已反映三期 MQ 结果回传能力 |
| 本次遗留风险已显式记录 | 是 | 已写入第 7 节 |
| 交付结论已明确 | 是 | 当前为“有条件可交付” |
