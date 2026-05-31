# implementation_report — recall-gateway

| 项 | 值 |
| :--- | :--- |
| 需求名 | recall-gateway |
| 分支 | feature/recall-gateway |
| 实现日期 | 2026-05-30 |
| 全量测试 | `mvn test` BUILD SUCCESS（link-* 全模块 0 失败） |
| 文档同步 | `check_docs_sync --working` 无 issue；`check_ai_links` OK |
| 状态 | 质量审查通过（REQUEST_CHANGES 两项已修，见 §8），回归绿，待提交 PR |

## 1. 实现摘要

```text
link-model/
  enums/ErrorCode.java                    # [改] 新增召回段 30001-30007
  enums/RecallSseError.java               # [新] SSE 英文串码 + 上游码/HTTP 映射
  dto/request/RecallStreamRequest.java    # [新] 入参 + @JsonAnySetter/@AssertTrue 拒绝未知字段
  dto/response/RecallHitDTO.java          # [新] 最小候选 chunkId/docId/datasetId
  dto/response/RecallDoneEvent.java       # [新] recall_done 载荷
  dto/response/RecallErrorEvent.java      # [新] error 载荷
  pom.xml                                 # [改] +jackson-annotations 2.12.3
link-core/
  security/InternalJwtSigner.java         # [新] 手写 HS256 JWT（JDK Mac + Base64URL）
  handler/GlobalExceptionHandler.java     # [改] +HttpMessageNotReadableException→400
link-service/
  config/RecallProperties.java            # [新] tolink.recall.*
  config/RecallExecutorConfig.java        # [新] recallStreamExecutor / recallOkHttpClient / recallJwtSigner
  recall/RecallScopeResolver.java         # [新] 归属校验 / 空列表展开本人全库
  recall/RecallRateLimiter.java           # [新] guava Cache<userId,RateLimiter> 按用户限流
  recall/RecallUpstreamClient.java        # [新] 上游调用抽象（便于 mock）
  recall/OkHttpRecallUpstreamClient.java  # [新] okhttp 手动读上游 SSE + 解析/映射
  recall/RecallUpstreamRequest.java       # [新] snake_case 内部请求体
  recall/RecallUpstreamListener.java      # [新] onDone/onError 回调
  recall/RecallUpstreamCall.java          # [新] 可取消句柄
  recall/ResolvedScope.java               # [新] 校验/展开结果
  RecallService.java + recall/RecallServiceImpl.java  # [新] 校验编排 + SSE 转发
  pom.xml                                 # [改] +guava +okhttp 4.12.0 +mockwebserver(test)
link-api/
  controller/RecallController.java        # [新] POST /api/v1/recall/stream
resources/application-{local,dev}.yml     # [改] +tolink.recall 段
.env.example                              # [改] +RECALL_*/RAG_PYTHON_BASE_URL
docs/reference/{api_contracts,error_codes}.md、docs/guides/configuration.md、
docs/architecture/project_structure.md、README.md、docs/development/testing.md  # [改] 文档同步
```

## 2. 相对 technical_design 的偏差（均不改变验收语义）

| # | TD 原方案 | 实际实现 | 原因 |
| :-- | :--- | :--- | :--- |
| 1 | jjwt 0.9.1 签发 JWT | 手写 HS256（JDK `Mac` + Base64URL，复用 jackson 序列化 claims） | jjwt 0.9.1 在 Java 17 依赖已移除的 `javax.xml.bind.DatatypeConverter`（NoClassDefFoundError）；手写零依赖、可控、可测，link-core 不需加 jjwt |
| 2 | `RecallUpstreamRequest` 放 link-model/dto/internal | 放 link-service/recall | link-model 主代码无 compile jackson-databind，且该 DTO 仅内部使用 |
| 3 | — | link-model 新增 `jackson-annotations` 2.12.3 | `RecallStreamRequest` 需 jackson 注解；根 pom 未 import spring-boot BOM，故显式写 version |
| 4 | `@JsonIgnoreProperties(ignoreUnknown=false)` 拒绝未知字段 | `@JsonAnySetter` 收集 + `@AssertTrue`（@Valid → 400） | `@JsonIgnoreProperties(ignoreUnknown=false)` 不强制抛异常（取决于 `FAIL_ON_UNKNOWN_PROPERTIES`，Spring Boot 默认关），实测多余字段被静默忽略 |
| 5 | 召回线程池复用 link-core 多池框架 | link-service 自建 `RecallExecutorConfig` | 避免改跨需求文件、保持召回内聚；okhttp 同步 execute() 在本池跑，不受 dispatcher perHost=5 限制 |
| 6 | `RecallServiceImpl` 注入转发线程池 | 移除（未使用） | 无库短路走同步 emit、正常路径的线程在 okhttp client 内部，service 不直接用 executor |
| 7 | handleNotReadable 返回 `RECALL_INVALID_REQUEST` 码 | 返回 HTTP 400 + 通用 message | 与现有 `handleValidationException` 风格一致；该 handler 是全局增强，非召回专属 |
| 8 | 超时映射 RECALL_TIMEOUT | 用 `canceledByClient` 标志区分超时与前端取消 | okhttp callTimeout 超时会内部 cancel call（`isCanceled()=true`），与前端主动取消无法靠 `isCanceled()` 区分——前者应映射 TIMEOUT，后者静默（测试 `Should_MapToTimeout` 抓到此 bug） |

## 3. 测试结果

- 全量 `mvn test`：**BUILD SUCCESS，0 失败**。
- 召回新增 6 个测试类共 26 个用例：`InternalJwtSignerTest`(1)、`RecallScopeResolverTest`(4)、`RecallRateLimiterTest`(2)、`RecallServiceImplTest`(5)、`OkHttpRecallUpstreamClientTest`(5，MockWebServer)、`RecallControllerTest`(9，@SpringBootTest+MockMvc+asyncDispatch)。
- 27 个 acceptance Scenario 的测试映射见 `technical_design.md` §10.2；建流后 SSE 内容用 `asyncDispatch` 断言，上游用 `@MockBean RecallUpstreamClient` 驱动，避免真实网络。

## 4. 文档同步

`api_contracts.md`（Recall 章节）、`error_codes.md`（召回双通道错误码）、`configuration.md`（§4.14 召回配置）、`project_structure.md`（依赖与模块）、`README.md`（核心能力 + API 概览）、`testing.md`（召回测试条目）、`.env.example`。`check_docs_sync --working` 与 `check_ai_links` 均通过。

## 5. 待联调项（跨端，需与 Python 对齐）

1. **内部 JWT 密钥编码**：当前用 secret 字符串的 UTF-8 字节作 HMAC key（`InternalJwtSigner`）；需与 Python 验签端确认（UTF-8 字节 vs hex 解码字节）。
2. **Python `recall_done` 的 hits 字段名**：当前按 `chunk_id` / `doc_id` / `dataset_id` 解析（`OkHttpRecallUpstreamClient.extractHits`）；需对 Python brief 核实。

## 6. 发布前置与回滚

- **发布前置**：各环境配置 `RAG_PYTHON_BASE_URL` 与 `RECALL_INTERNAL_JWT_SECRET`（与 Python 一致，否则召回签发 JWT 阶段 500）；部署网关对 `/api/v1/recall/stream` 关闭响应缓冲。
- **回滚**：纯新增接口 + 一个通用异常 handler 增强 + 配置项，不改表/不改 MQ；移除/不暴露 `RecallController` 即可，其余新增类无副作用。

## 7. 风险

- 召回 SSE 为长连接，Java 中转占用转发线程池/连接资源（brief §9 已接受，首版用有界池 + 按用户限流缓解，后续 `LINK-40` 短期 token 直连演进）。

## 8. 质量审查与修复（code-review-and-quality，2026-05-30）

审查结论 **REQUEST_CHANGES**：2 个 Required + 4 个 Suggestion。已修两个 Required 与一个 Suggestion，回归绿。

| 项 | 严重度 | 问题 | 修复 |
| :-- | :-- | :--- | :--- |
| R1 | Required | `SseEmitter` 超时与 okhttp `callTimeout` 同为 `stream-timeout-ms`，等长计时器竞争；若 SseEmitter 先超时则取消上游并静默关流、**不发 `RECALL_TIMEOUT`**，违反 Scenario 19（且 SseEmitter 计时起点更早，更易先触发） | 新增配置 `emitter-timeout-buffer-ms`（默认 5000）；emitter 超时 = `stream-timeout-ms` + 缓冲，使 okhttp 超时稳定先触发并发出 `RECALL_TIMEOUT`，SseEmitter 超时退为兜底。`RecallProperties`/yml/.env/configuration.md 同步；`RecallServiceImplTest` 加超时不变量断言 |
| R2 | Required | guava `RateLimiter.create(perMinute/60)` 是匀速令牌桶，实为「约 1 次/6 秒、不可突发」，与 brief/契约「每用户每分钟 10 次」语义不符（用户 6 秒内重发即被 429） | 用户选方案 (a)：改为**固定窗口计数**（60s 窗口内允许突发到上限，超限返回 429，窗口滚动重置）。`RecallRateLimiter` 重写（仍用 guava `Cache` 持有各用户窗口）；`RecallRateLimiterTest` 改为突发/隔离/窗口滚动 3 用例（注入时钟测时间推进） |
| S2 | Suggestion | `link-model/pom.xml` 注释写 `@JsonIgnoreProperties`，与实际 `@JsonAnySetter`（偏差 #4）不符 | 更正注释 |

**修复中自身回归（全量回归捕获）**：R2 给 `RecallRateLimiter` 加包级 2 参构造器（注入时钟）后，类有两个构造器，`@Component` 无法自动装配（`No default constructor` → 所有 `@SpringBootTest` 上下文加载失败，`RecallControllerTest` 因 `@MockBean` 该 bean 而幸免，故首轮仅其它 controller 测试红）。修复：给生产单参构造器加 `@Autowired`。

**未做的 Suggestion（留后续）**：S1（`RECALL_INVALID_REQUEST/INTERNAL_AUTH_FAILED/ALL_SOURCES_FAILED/TIMEOUT/UPSTREAM_ERROR` 5 个数字 `ErrorCode` 主代码未引用，且其 502/504 httpStatus 误导——牵动 error_codes.md 契约，建议单独决策删/留）；S3（JWT 在请求线程同步签发，空密钥→HTTP 500 而非 SSE error，可启动期 fail-fast 或挪入异步）；S4（`assertUserActive` 的 DB 读先于限流，可限流前置以在滥用下卸载 DB）。

**回归**：全量 `mvn test` BUILD SUCCESS（117，0 失败，含召回 6 测试类 **28** 用例）；`check_docs_sync --working`（45 文件，无问题）、`check_ai_links` 通过。
