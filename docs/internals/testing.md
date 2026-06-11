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

## 日志与可观测测试

- 链路追踪/访问/审计组件在 `link-core/src/test/java/com/qingluo/link/core/` 下用纯 JUnit/Mockito + `MockHttpServletRequest/Response` 承接：`trace/TraceContextTest`（traceId 生成、白名单防注入、MDC 读写清理）、`trace/TraceIdFilterTest`（复用/新建/注入拒绝/响应头/请求后清理）、`trace/MdcTaskDecoratorTest`（透传与执行后清理，含异常路径）、`web/AccessLogFilterTest`（放行/不吞异常/文档静态路径跳过）、`log/AuditLogTest`（写入 `AUDIT` logger + `action=` 前缀 + 占位渲染，用 logback `ListAppender` 断言）。
- 审计埋点调用 `AuthContext.getCurrentUserId()` 取操作人，该方法在无 Web 上下文/未登录时降级返回 `null`（不抛异常），故 Service 纯单测无需搭建 sa-token 上下文。

## Spec-as-Test 要求

- `acceptance.feature` 中的每个 Scenario 必须在 TD 中映射到测试。
- Java 测试不必逐字使用 Gherkin 名称，但测试方法、注释或 TD 映射必须能追溯。
- 外部 MySQL、Redis、Kafka、MinIO、第三方 API 在单元测试中默认 Mock。
- 全局最近文档等跨数据集查询应在 Controller/集成测试中覆盖当前用户权限隔离、稳定排序、分页和空列表返回。
- 缓存一致性变更必须分别测试读/回填故障的可用性降级，以及同步删缓存失败的错误传播，不能用降级行为掩盖写路径一致性失败。
- 缓存一致性组件改造优先在 `link-service/src/test/java/com/qingluo/link/service/cache/CacheConsistencyServiceTest.java` 承接，至少覆盖：事务提交后首删、事务回滚不删、无事务立即删、首删失败不改请求结果、补偿第二删强失败语义，以及同事务多次触发下的 key 去重结果。
- 文档文件上传配置运行时来源为 Redis；Controller 测试应 Mock 或重置 `DocumentFileConfigCacheService`，不再依赖数据库配置表。
- 解析链路测试需覆盖 schema 初始化、扁平 MQ 契约、上传初始化 `document_parse_file`、解析投递事务回滚、重复提交拦截、结果归属校验和 SSE 转发；Java 端不应在结果消费测试中回写 Python 负责的终态字段。
- parse_result 消费兜底测试：失败分类与当前任务过滤用 Mockito 单测（`DocumentParseResultServiceImplTest`、`DocumentParseStuckScannerTest`、`ParseResultKafkaConfigTest`）；“坏消息不阻塞后续 / 缓存补偿隔离”用 `@EmbeddedKafka` 集成测试（`ParseResultConsumerEmbeddedKafkaTest`，按 `*Test` 命名以纳入 Surefire）。所有用例须断言 Java 不写业务表（`verify(...never()).updateById`）。
- 解析重试链路测试：入口分类（首次/重试/已成功/运行中）与重试消息构造用 `DocumentParseTaskServiceImplTest`，消息完整性校验用 `DocumentParseTaskMQTest`，重试链回溯边界（链长 1 / 链断 / 深度上限 / 防环）用 `DocumentParseRetryChainServiceImplTest`。终态判定与卡住扫描已从 `document_parsed_log.task_status`（已删）迁到 `document_parse_pipeline.pipeline_status`，相关 Mockito/集成测试以 pipeline 行驱动；`DocumentParseTaskServiceImplTest` 因走 `LambdaUpdateWrapper.set` 需 `@BeforeAll` 调 `TableInfoHelper.initTableInfo` 预热 TableInfo 缓存。
- 文档上传异步化测试：同步快速失败/同名复用用 `DocumentFileServiceImplTest`，终态守卫回写与解析投递时机用 `DocumentUploadStatusWriterTest`，OSS 失败/池满拒绝/孤儿用 `DocumentUploadAsyncExecutorTest`，超时扫描用 `DocumentUploadStuckScannerTest`，临时文件物化/启动清理用 `DocumentUploadTempStorageTest`，线程池多池就绪/校验用 `ThreadPoolConfigTest`（`ApplicationContextRunner`）。
  - 在不启动 MyBatis 的纯 Mockito 单测里构建 `LambdaUpdateWrapper` 需先在 `@BeforeAll` 调 `TableInfoHelper.initTableInfo(...)` 初始化实体的 MP 列缓存，否则报 “can not find lambda cache”。
  - 集成测试（`DocumentFileControllerTest`）中上传接口响应恒为 `UPLOADING`（终态异步回写）；测试用“新线程执行并 join”的执行器覆盖 `documentUploadExecutor`（`spring.main.allow-bean-definition-overriding=true`），保证异步在独立线程获得全新事务、其 afterCommit 自动解析投递正常触发且断言确定。
- 数据集/文件隐性删除测试：软删语义、不删 OSS、会话/消息物理删、afterCommit 删除通知按范围分流（删数据集 `notifyDatasetDeleted`、删文件 `notifyFileDeleted`，回滚/未授权 `never()`）用 `DatasetServiceImplTest`、`DocumentFileServiceImplTest`（Mockito 单测，`@BeforeAll` 初始化 `Dataset`/`DocumentOriginalFile` 的 MP 列缓存）；同名重传多轮不撞唯一约束、删后重建同名数据集、死行连同 `object_key` 留存、软删文件内部下载 404 用 `SoftDeleteReuseIntegrationTest`（`@SpringBootTest`+H2，经 Mapper 造行 + Service 软删，绕过异步上传保证确定）；级联软删/物理删的库态断言在 `DatasetControllerTest`、`DocumentFileControllerTest`。`ChatConversation` 去软删后 `ChatConversationTest` 不再断言 `is_deleted`/`@TableLogic`。
- 删除通知 MQ 测试：删除通知契约（topic `tolink.rag.document_delete`、`QUEUE`、扁平 snake_case、`delete_type` 分流、dataset 范围省略 `original_file_id`、缺字段拒发）用 `DocumentDeleteNotifyMQTest`（仿 `DocumentParseTaskMQTest`）；producer 投递载荷与「发送失败 / 发送器缺失吞掉不外抛」用 `DocumentDeleteNotifierTest`（Mockito mock `ObjectProvider<MQSend>`，`ArgumentCaptor` 断言消息字段）。
- 召回网关（recall-gateway）测试：建流前校验（未登录/用户禁用/越权/参数/多余字段/限流）与建流后 SSE（recall_done 裁剪、error 映射、空库空 done）用 `RecallControllerTest`（`@SpringBootTest`+MockMvc，`StpUtil.login` 造登录态，`@MockBean` 上游客户端与限流器，建流后用 `asyncDispatch` 断言 SSE 内容）；scope 校验/展开用 `RecallScopeResolverTest`、限流用 `RecallRateLimiterTest`（固定窗口：窗口内突发到上限后拒、按用户隔离、窗口滚动重置，注入时钟测试时间推进）、编排自洽（body↔JWT、建流前失败不调 Python 不变量）用 `RecallServiceImplTest`（Mockito，`@BeforeAll` 初始化 `Dataset` 的 MP 列缓存）；上游 SSE 解析/超时/取消用 `OkHttpRecallUpstreamClientTest`（`MockWebServer`）；HS256 JWT 签发用 `InternalJwtSignerTest`。Controller 测试须 `@MockBean InternalJwtSigner`，避免依赖真实 JWT 密钥配置（空密钥会在 `Mac.init` 抛异常）。
- LLM 配置重构（厂商—模型—能力三层 + 系统预设 + 两步配置）测试：目录按能力聚合/空厂商过滤用 `SystemProviderServiceImplTest`、`ProviderModelServiceImplTest`（含管理员新增模型即时反映、幂等上架）；配置厂商整厂商展开 + Key 厂商级共用 + 加密脱敏 + 模型启停 + 按能力选生效（含 `MODEL_DISABLED`/`MODEL_NOT_SUPPORTED` 拒绝）+ 预设删除拦截（`PRESET_READONLY`）用 `UserLLMConfigServiceImplTest`（走 `LambdaUpdateWrapper.set`，`@BeforeAll` 调 `TableInfoHelper.initTableInfo` 初始化 `UserLLMConfig` 的 MP 列缓存）；注册写预设/同能力单生效/写入幂等用 `SystemPresetServiceImplTest` 与 `AuthServiceImplTest`（`@Mock SystemPresetService`，断言 `applyPresetsForNewUser` 被调用）；两步接口端到端用 `ConfigControllerTest`（`@SpringBootTest`+H2，setup 先插 `llm_provider_model` 目录，再 setup-provider→effective→toggle-model）。
- 召回 session token 签发（recall-session，LINK-104）测试：前端直连签发的 claims（`aud=tolink-rag-frontend`/`scope=recall:stream`/带 `iat`、无 `jti`）、HS256 与密钥隔离用 `RecallSessionJwtSignerTest`；编排（用户状态校验、归属校验复用、`streamUrl` 拼接、越权/禁用/非正整数 `sub` 不签发）用 `RecallSessionServiceImplTest`（Mockito）；端到端鉴权与签发响应用 `RecallSessionControllerTest`（`@SpringBootTest`+MockMvc，`StpUtil.login` 造登录态，H2 造数据集，复用真实 resolver/signer——测试 yml 须配置 `tolink.recall.session-jwt-secret` 且与 `internal-jwt-secret` 不同，否则 `RecallExecutorConfig` 启动期 fail-fast）。
- 博客测试：`BlogContentStorageServiceImplTest` 覆盖 Markdown UUID Key、UTF-8/后缀校验、无业务大小限制、data URI 自动改写、受限网络图片保留原 URL、相对路径拒绝、已知公共 URL 跳过和图片 MIME；`BlogControllerTest` 使用 `@SpringBootTest` + H2 + local OSS 覆盖管理员创建/导入/保存/发布、后端 UUID slug、Markdown 内联图片自动入 OSS 和 `blog_asset`、公开列表不返回正文、公开详情、普通用户 403、封面资源软删、`CONTENT_IMAGE` 上传返回 `markdownText`、资源筛选、正文引用图片删除拒绝和无下载路由。Sa-Token HTTP 角色校验依赖 `SaTokenAnnotationConfig`，`StpInterfaceImplTest` 必须覆盖字符串形式 loginId。
- 反馈模块测试：`FeedbackServiceImplTest` 覆盖匿名提交默认值、方案甲只存 `attachment_object_key`（公开桶上传返回 URL 但落库只取 object key、不含 `http`/桶名）、非法 `type` 拒绝、入库失败时用 `deleteFile(PUBLIC, objectKey)` 清理刚上传的公开附件；`AdminFeedbackServiceImplTest` 覆盖管理员分页、状态终态写 `processed_at`、优先级更新、回复不自动改状态、由 object key 拼出 `attachmentUrl`；`OssApplicationServiceImplTest` 与 `OssObjectKeyGeneratorTest` 覆盖 `feedback/yyyy/MM/{uuid}.{suffix}` 公开桶 object key 规则（精度到月、不含日）；`MinioFileServiceTest` 覆盖公开桶名解析、`resolvePublicUrl` 拼装与公开桶未配置快速失败。
