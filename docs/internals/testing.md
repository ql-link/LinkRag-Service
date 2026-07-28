# Testing

## 测试分层

| 类型 | 位置 | 说明 |
| --- | --- | --- |
| 单元测试 | 各模块 `src/test/java` | 使用 JUnit/Mockito，隔离外部依赖 |
| Controller 测试 | `link-api/src/test/java` | 使用 MockMvc 或 Spring 测试配置 |
| Service 测试 | `link-service/src/test/java` | 校验业务分支、事务边界、Mapper/MQ/缓存交互 |
| 组件测试 | `link-components/*/src/test/java` | 校验 Redis/MQ/OSS 组件边界 |

## 命令

```bash
mvn clean test
mvn -pl link-api test
mvn -pl link-service test
```

## 执行约定

- 根 `pom.xml` 固定 Maven Surefire `2.22.2`，确保各模块的 JUnit 5 测试都会执行。
- 提交前使用 `mvn clean test`；删除或移动 Java 类后，干净构建可避免旧 `target/classes` 影响结果。
- Profile 中可提交的非密钥默认值发生变更时，使用纯资源契约测试锁定目标环境；例如
  `DevProfileConfigurationTest` 必须确保 `application-dev.yml` 只指向 `tolink_rag_dev` 和
  `tolink-dev-*` bucket，避免依赖旧容器环境变量掩盖配置漂移。

## 日志与可观测测试

- 链路追踪/访问/审计组件在 `link-observability/src/test/java/com/qingluo/link/observability/` 下用纯 JUnit/Mockito + `MockHttpServletRequest/Response` 承接：`trace/TraceContextTest`（trace_id 生成、白名单防注入、MDC 主键 `trace_id` 与旧 `traceId` 兼容双写、读写清理）、`trace/TraceIdFilterTest`（复用/新建/注入拒绝/响应头/请求后清理）、`trace/MdcTaskDecoratorTest`（透传与执行后清理，含异常路径）、`web/AccessLogFilterTest`（放行/不吞异常/文档静态路径跳过）、`log/AuditLogTest`（写入 `AUDIT` logger + `action=` 前缀 + 占位渲染，用 logback `ListAppender` 断言）。
- 管理端日志查询代理测试分层：`link-service` 的 `LokiLogQueryBuilderTest` 覆盖 service/level/trace_id/keyword 安全 LogQL，`LokiLogParserTest` 覆盖 Java JSON Lines、Python Loguru `serialize=True` 与非 JSON 原始行，`AdminLogQueryServiceImplTest` 覆盖 Loki labels 正常/异常兜底；`link-api` 的 `AdminLogControllerTest` 覆盖 ADMIN 可访问、普通 USER 403 和 snake_case 响应字段。
- MQ trace header 测试：`toLink-components-mq` 的 `KafkaMQSendTest` 覆盖发送时从 MDC 写入 `X-Trace-Id` header 且显式 header 不被覆盖；`link-service` 的 `KafkaTraceHeadersTest` 覆盖 Python header 别名读取，`ChatTurnKafkaReceiverTest` / `UsageReportKafkaReceiverTest` 覆盖 Kafka header 恢复到 MDC 后再进入业务 receiver，并在 finally 清理。
- 审计埋点调用 `AuthContext.getCurrentUserId()` 取操作人，该方法在无 Web 上下文/未登录时降级返回 `null`（不抛异常），故 Service 纯单测无需搭建 sa-token 上下文；访问日志的用户 ID 通过 `link-core` 的 `AuthCurrentUserProvider` 桥接到 `link-observability`，观测模块自身不依赖 Sa-Token。

## Spec-as-Test 要求

- `acceptance.feature` 中的每个 Scenario 必须在 TD 中映射到测试。
- Java 测试不必逐字使用 Gherkin 名称，但测试方法、注释或 TD 映射必须能追溯。
- 外部 MySQL、Redis、Kafka、MinIO、第三方 API 在单元测试中默认 Mock。
- 用户资料类 multipart 上传接口在 `UserControllerTest` 用 local OSS 测试配置承接端到端回写断言；对应 Service 单测用 `OssApplicationService` mock 覆盖 OSS 返回值和数据库更新。资料与角色读取测试应直接断言 `SysUserMapper` 查询。
- 全局最近文档等跨数据集查询应在 Controller/集成测试中覆盖当前用户权限隔离、稳定排序、分页和空列表返回。
- 管理端用户统计看板测试：`AdminUserStatisticsServiceImplTest` 使用固定 `Clock` 覆盖 7/30/90 天、上海时区半开边界、趋势补零、日/周期去重和零基数环比；`UserLoginEventRecorderImplTest` 覆盖事件写入及失败不影响认证；`AuthServiceImplTest` 断言登录/注册成功记录事件、失败登录不记录；`AdminControllerTest` 使用 H2 真实聚合 SQL 覆盖 USER+ADMIN 规模、重复登录去重、响应无身份信息和非法范围 400。
- 数据集解析/检索配置测试：`DatasetParseConfigControllerTest` 覆盖 GET/PUT 往返、创建数据集固化稀疏/稠密 `configId`、两者不可重绑、CHAT/VISION/RERANK 条件必需与可替换/清空、`enable_rerank`、固定 weighted score 三路权重、历史 `recall_fusion_strategy` 字段忽略且不再落库、字段级错误详情及配置参数边界；`DatasetParseConfigServiceImplTest` 覆盖无行默认、写入归一化和 Mapper insert/update；`DatasetModelBindingValidatorTest` 覆盖五类能力、存在/active/归属/能力固定错误优先级，`DatasetControllerTest` 覆盖创建时统一配置绑定落库。
- 缓存一致性变更必须分别测试读/回填故障的可用性降级、业务写提交后首次失效失败不改变请求结果，以及 CDC/MQ 补偿删除失败的强失败与 DLT 投递，不能用读路径降级掩盖写路径一致性问题。
- 缓存一致性组件改造优先在 `link-service/src/test/java/com/qingluo/link/service/cache/CacheConsistencyServiceTest.java` 承接，至少覆盖：事务提交后首删、事务回滚不删、无事务立即删、首删失败不改请求结果、补偿第二删强失败语义，以及同事务多次触发下的 key 去重结果。
- Redis 读保护在 `toLink-components-redis` 测试中覆盖命中、并发 miss 单次回源、读写异常精确指标、空值短 TTL 和 fence 变化跳过旧值回填；key 路由测试必须断言无人工版本段。
- 数据库镜像缓存 owner 测试需覆盖 1～7 天 TTL、CDC/readiness 门禁、数据集权限仍查权威数据、用户登录/授权不使用资料缓存，以及博客固定前 100 条索引、越界页直查库。
- 文档上传配置测试分层：`DocumentFileConfigStoreTest` 覆盖 Redis key 缺失、损坏、不可用和最后有效快照回退；`AdminDocumentFileConfigServiceImplTest` 覆盖完整 PUT、硬上限/后缀全集校验、Redis 写失败不更新本地状态；`AdminControllerTest` 覆盖 GET/PUT 和历史 PATCH 405。
- 文档文件名校验测试需同时覆盖“常见业务符号允许”和“安全边界拒绝”：`DocumentFileServiceImplTest` 承接归一化、长度/控制字符等快速失败分支，`DocumentFileControllerTest` 承接 HTTP 上传后 `original_filename` 落库与返回值。
- 解析链路测试需覆盖 schema 初始化、扁平任务 MQ 契约、上传初始化 `document_parse_file`、解析投递事务回滚、重复提交拦截和 `parse-results` 结果查询；PDF 解析任务需覆盖数据集级 `pdf_config.pdf_parser_backend` 透传为 `pdf_parser_backend`，以及非 PDF/未配置时不补默认值；文档解析 SSE 与过程事件回调已下线，不再新增实时推送测试；Java 端不消费 Python 解析终态 MQ，也不回写 Python 负责的终态字段。
- 解析重试链路测试：入口分类（首次/重试/已成功/运行中）与重试消息构造用 `DocumentParseTaskServiceImplTest`，消息完整性校验用 `DocumentParseTaskMQTest`，重试链回溯边界（链长 1 / 链断 / 深度上限 / 防环）用 `DocumentParseRetryChainServiceImplTest`。终态判定已从 `document_parsed_log.task_status`（已删）迁到 `document_parse_pipeline.pipeline_status`，相关 Mockito/集成测试以 pipeline 行驱动；`DocumentParseTaskServiceImplTest` 因走 `LambdaUpdateWrapper.set` 需 `@BeforeAll` 调 `TableInfoHelper.initTableInfo` 预热 TableInfo 缓存。
- 文档上传异步化测试：同步快速失败/同名复用用 `DocumentFileServiceImplTest`，终态守卫回写与解析投递时机用 `DocumentUploadStatusWriterTest`，OSS 失败/池满拒绝/孤儿用 `DocumentUploadAsyncExecutorTest`，超时扫描用 `DocumentUploadStuckScannerTest`，临时文件物化/启动清理用 `DocumentUploadTempStorageTest`，线程池多池就绪/校验用 `ThreadPoolConfigTest`（`ApplicationContextRunner`）。
  - Markdown 资源包新增分层：`MarkdownAssetReferenceScannerTest` 覆盖标准/引用式/HTML/Obsidian 与转义改写；`MarkdownAssetPathResolverTest` 覆盖 FULL_PATH、SHALLOW_BASENAME、percent 候选、大小写和歧义；`MarkdownAssetContentValidatorTest` 覆盖 magic/MIME/扩展/单图上限；`MarkdownAssetManifestStoreTest` 覆盖清单归属与失败关闭；`DocumentUploadAsyncExecutorTest` 必须断言旧 manifest 先删、新 manifest 最后上传；`DocumentUploadStatusWriterTest` 必须断言软问题上传成功但不自动解析；`DocumentParseTaskServiceImplTest` 覆盖 409 summary、ignore 与 CAS 同 taskId。
  - 在不启动 MyBatis 的纯 Mockito 单测里构建 `LambdaUpdateWrapper` 需先在 `@BeforeAll` 调 `TableInfoHelper.initTableInfo(...)` 初始化实体的 MP 列缓存，否则报 “can not find lambda cache”。
  - 集成测试（`DocumentFileControllerTest`）中上传接口响应恒为 `UPLOADING`（终态异步回写）；测试用“新线程执行并 join”的执行器覆盖 `documentUploadExecutor`（`spring.main.allow-bean-definition-overriding=true`），保证异步在独立线程获得全新事务、其 afterCommit 自动解析投递正常触发且断言确定。
- 数据集/文件隐性删除测试：软删语义、不删 OSS、会话/消息物理删、afterCommit 删除通知按范围分流（删数据集 `notifyDatasetDeleted`、删文件 `notifyFileDeleted`，回滚/未授权 `never()`）用 `DatasetServiceImplTest`、`DocumentFileServiceImplTest`（Mockito 单测，`@BeforeAll` 初始化 `Dataset`/`DocumentOriginalFile` 的 MP 列缓存）；同名重传多轮不撞唯一约束、删后重建同名数据集、死行连同 `object_key` 留存、软删文件内部下载 404 用 `SoftDeleteReuseIntegrationTest`（`@SpringBootTest`+H2，经 Mapper 造行 + Service 软删，绕过异步上传保证确定）；级联软删/物理删的库态断言在 `DatasetControllerTest`、`DocumentFileControllerTest`。`ChatConversation` 去软删后 `ChatConversationTest` 不再断言 `is_deleted`/`@TableLogic`。
- 删除通知 MQ 测试：删除通知契约（topic `tolink.rag.document_delete`、`QUEUE`、扁平 snake_case、`delete_type` 分流、dataset 范围省略 `original_file_id`、缺字段拒发）用 `DocumentDeleteNotifyMQTest`（仿 `DocumentParseTaskMQTest`）；producer 投递载荷与「发送失败 / 发送器缺失吞掉不外抛」用 `DocumentDeleteNotifierTest`（Mockito mock `ObjectProvider<MQSend>`，`ArgumentCaptor` 断言消息字段）。
- 对话轮次落库测试（chat_turn，后台续跑 + 可靠落库 chat-stream-resilient-persist；LINK-191 起本通道只落对话内容、不写 `llm_usage_log`）：信封解包（先取 `payload` 再反序列化、snake_case 映射、兼容扁平）、`status` 三态（`GENERATING`/`COMPLETED`/`FAILED`）枚举、`turn_id`/`request_id` 必填、`GENERATING` 起点 `provider_type` 空、`FAILED` 携带 `error_code`/`error_message`、Python 上报 `title` 映射、载荷已无 token 字段用 `ChatTurnMQTest`；按 `turn_id` upsert（`GENERATING` 起点插行、终态更新同行补 `error_*`、`GENERATING`→`COMPLETED` 推进同一行、终态后迟到 `GENERATING` 不回退、重复终态/重复 `GENERATING` 幂等跳过）、`conversation_id` 归属不匹配/会话缺失丢弃、`title` 仅在当前标题为空或默认时写入且不覆盖手动标题用 `ChatTurnPersistenceServiceImplTest`（Mockito 单测，`@BeforeAll` 初始化 `ChatMessage`/`ChatConversation` 的 MP 列缓存，不再 mock `UsageLogMapper`）；端到端按 `turn_id` upsert/状态不回退/错误字段落库/归属拒写、且全程断言 `llm_usage_log` 维持为空用 `ChatTurnIntegrationTest`（H2 真实链路）。`ChatMessageTest` 断言一行一轮字段（含 `turn_id`/`query`/`answer`/`references` JSON+`JacksonTypeHandler`/`request_id`/`status`/`error_code`/`error_message`）并断言旧字段（`role`/`content`/`token_count`）已移除；H2 测试库 `references` 降级 VARCHAR 存 JSON 文本、`turn_id` 唯一索引 `uk_chat_message_turn_id`。对话消息读取接口由 `ChatControllerTest` 真实 H2 集成覆盖，需断言同一会话下按 `created_at` 正序分页返回 `turnId`/`query`/`answer`/`configId`/`modelName`/`references`/`requestId`/`status`/`errorCode`/`errorMessage`，并返回在途 `GENERATING` 消息。历史召回片段恢复的批量 Chunk 详情接口由 `KnowledgeChunkControllerTest` 覆盖，需断言按请求 chunk id 顺序返回、过滤越权/REMOVED/不存在片段、回填 `document_original_file.original_filename`、未登录 401。
- 统一 Token 用量上报测试（usage_report，LINK-184 起，LINK-191 起承载全部模型调用含对话 generate）：信封解包与字段映射（`user_id` string→Long、可空字段缺省、vision 真实 `completion_tokens`）、`stage`/`operation` 枚举与必填 token 校验、出站序列化往返、瘦身后旧 `conversation_id`/`request_id` 仍被旧上游发送时忽略不报错用 `UsageReportMQTest`；落库字段映射、`config_id`/`latency_ms` 缺省落 NULL、`status` 缺省补 `success`、token 缺省补 0、`stage=chat`/`operation=generate` 行按通用路径落库（含 `status=failed`）用 `UsageReportPersistenceServiceImplTest`（Mockito 单测，仅 mock `UsageLogMapper`）。`UsageLogTest` 断言瘦身后实体已移除 `fallbackConfigId`/`conversationId`/`messageId`/`requestId` 字段。
- 用量聚合接口测试（LINK-182，`UsageControllerTest` 真实 H2 集成）：种子注入两条 chat 行 + 一条 parse·embed 行后，断言 `summary` 缺省 chat 口径的 `successCalls`/`failedCalls`/`successRate`；`by-model` 全链路 GROUP BY 三模型、按 `totalTokens` 降序（验证 `selectMaps` 经 `getString`/`getLong` 对 H2 大写列名的大小写兼容取值）；`trend` 当前周期合计与上一周期为空时 `tokenGrowthRate`/`callGrowthRate` 为 `null`（Jackson 默认序列化 null，用 hamcrest `nullValue()` 断言）。`stage` 过滤（缺省排除 parse、`stage=all` 纳入、明细暴露 `stage`/`operation`）亦在本测试覆盖。
- 召回数据集范围校验测试：scope 校验/展开（非空全归属校验、空入参展开本人全库、含软删/越权拒绝）用 `RecallScopeResolverTest`（Mockito，`@BeforeAll` 初始化 `Dataset` 的 MP 列缓存）。该 resolver 由召回 session 签发链路复用。
  > 旧召回网关链路（Java 中转代理 `/api/v1/recall/stream`）已于 LINK-122 废弃移除，其测试 `RecallControllerTest` / `RecallServiceImplTest` / `RecallRateLimiterTest` / `OkHttpRecallUpstreamClientTest` / `InternalJwtSignerTest` 随之删除。
- 统一 LLM 配置测试：`LLMModelConfigValidatorTest` 锁定“不存在→停用→越权→能力不匹配”优先级；`LLMCapabilityDefaultServiceImplTest` 覆盖 USER 默认可指向本人 USER/共享 SYSTEM 配置、清除后保持未设置以及配置失效时的指针清理；`LLMModelConfigServiceImplTest` 覆盖 SYSTEM/USER 保存、加密脱敏、SYSTEM 配置失效时批量清理 USER 默认与引用删除保护；`ConfigControllerTest` 和 `AdminLLMConfigControllerTest` 覆盖 configId-only DTO、旧身份别名不存在、用户默认 PUT/DELETE 接口和管理端原子保存。厂商/模型目录的 N+1、协议、入口与候选审核仍由 `SystemProviderServiceImplTest`、`ProviderModelServiceImplTest`、`ProviderModelSyncServiceImplTest` 承接。
  - 厂商排序由 `AdminProviderServiceImplTest` 覆盖完整 ID 集校验、重复/缺失拒绝和连续优先级重排；`AdminControllerProviderTest` 使用 H2 覆盖 `PUT /api/v1/admin/providers/order` 的请求到落库链路。
  - 管理端模型配置测试需覆盖目录复制与目录变更二选一、API Key 重新加密、同能力多条 SYSTEM 配置、停用时批量清理用户默认、数据集引用保护和脱敏输出；管理端不测试平台默认关系。
  - 外部模型目录候选同步（LINK-50）测试：`ProviderModelSyncServiceImplTest` 覆盖手动刷新只写 `llm_provider_model_sync_job` / `llm_provider_model_sync_candidate`、新增/匹配/疑似过期统计、重复刷新同一 `(providerId, syncSource, modelName, inferredCapability)` 时更新候选而非重复插入、单能力发布与同模型多能力事务发布复用 `ProviderModelService.addModelCapability` 写入正式目录、候选审核状态更新；测试不得依赖真实 `models.dev` 网络返回，外部源通过 `ExternalModelCatalogClient` mock。
  - LLM 能力白名单由 `LLMCapabilityServiceImplTest` 守护，当前合法能力为 `CHAT` / `EMBEDDING` / `SPARSE_EMBEDDING` / `VISION` / `RERANK` / `ASR`；`OCR` 不再作为独立能力。涉及多能力聚合或整厂商展开的测试应至少覆盖 `SPARSE_EMBEDDING`，避免 seed/接口文档与校验白名单再次漂移。
  - LLM 协议与入口测试：`LLMProtocolServiceImplTest` 守护协议枚举，`ProviderModelServiceImplTest` 守护目录事实，`LLMModelConfigServiceImplTest` 守护运行快照只复制目录模型能力事实、不回退厂商模板。Python 按 `protocol + capability` 选 adapter。
- 用户与 LLM 读取测试：登录、账号状态、角色权限和 Java 配置校验必须读取 MySQL 权威数据；只有 Python 单条 runtime repository 可以使用 LLM Redis 缓存。缓存测试必须覆盖 hit/miss、fence 阻止旧值回填、Redis 故障回源和日志不泄漏密钥。
- 召回 session token 签发（recall-session，LINK-104）测试：前端直连签发的 claims（`aud=tolink-rag-frontend`/`scope=recall:stream`/带 `iat`、无 `jti`）、HS256 与密钥隔离用 `RecallSessionJwtSignerTest`；编排（用户状态校验、归属校验复用、数据集稀疏/稠密向量模型绑定缺失时拒签、`streamUrl` 拼接为 `<base>/api/v1/rag/stream`、越权/禁用/非正整数 `sub` 不签发）用 `RecallSessionServiceImplTest`（Mockito）；端到端鉴权与签发响应（断言 `streamUrl` 以 `/api/v1/rag/stream` 结尾——LINK-138 修正 Python 端点改名）用 `RecallSessionControllerTest`（`@SpringBootTest`+MockMvc，`StpUtil.login` 造登录态，H2 造数据集，复用真实 resolver/signer——测试 yml 须配置非空 `tolink.recall.session-jwt-secret`，否则 `RecallExecutorConfig` 启动期 fail-fast）。
- CDC 缓存补偿桥接测试：`CdcBridgeServiceTest` 覆盖 dataset current/old route、`llm_model_config` current/delete-old 精确路由、`sys_user` 登录时间字段忽略、缺 route 坏事件、稳定事件 ID、目标去重和 confirmed send 失败；测试不得查询 Redis 或数据库解析 route。`CacheKeyRouterTest` 断言 Dataset 与 LLM 两类跨语言缓存三键同槽；`DatasetParseConfigCacheContractTest` 固定版本化 envelope、snake_case 原始投影及“Java 默认不入缓存”；`CacheReadProtectionServiceTest` 覆盖未知版本/坏值回源；`LLMRuntimeCacheReadinessTest`、`LLMRuntimeCacheHealthIndicatorTest` 覆盖独立门禁；Kafka 配置测试覆盖最多 3 次 delivery、失败指标与 DLT。
- 博客测试：`BlogContentStorageServiceImplTest` 覆盖 Markdown UUID Key、UTF-8/后缀校验、无业务大小限制、data URI 自动改写、受限网络图片保留原 URL、相对路径拒绝、已知公共 URL 跳过和图片 MIME；`BlogPostServiceImplTest` 覆盖固定容量发布索引与越界页直查库；`BlogControllerTest` 使用 `@SpringBootTest` + H2 + local OSS 覆盖管理员创建/导入/保存/发布、后端 UUID slug、公开列表不返回正文、首次详情 `ETag + public,no-cache`、匹配 `If-None-Match` 在 OSS 前 304、同秒元数据更新改变 ETag、404/OSS 失败 `no-store`，以及资源上传/删除规则。Sa-Token HTTP 角色校验依赖 `SaTokenAnnotationConfig`，`StpInterfaceImplTest` 必须覆盖字符串形式 loginId。
- 反馈模块测试：`FeedbackServiceImplTest` 覆盖匿名提交默认值、方案甲只存 `attachment_object_key`（公开桶上传返回 URL 但落库只取 object key、不含 `http`/桶名）、非法 `type` 拒绝、入库失败时用 `deleteFile(PUBLIC, objectKey)` 清理刚上传的公开附件；`AdminFeedbackServiceImplTest` 覆盖管理员分页、状态终态写 `processed_at`、优先级更新、回复不自动改状态、由 object key 拼出 `attachmentUrl`；`OssApplicationServiceImplTest` 与 `OssObjectKeyGeneratorTest` 覆盖 `feedback/yyyy/MM/{uuid}.{suffix}` 公开桶 object key 规则（精度到月、不含日）；`MinioFileServiceTest` 覆盖公开桶名解析、`resolvePublicUrl` 优先使用 `tolink.oss.public-base-url`、未配置时回退 MinIO endpoint 拼装，以及公开桶未配置快速失败。
