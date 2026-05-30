# feature_info

| 项 | 值 |
| :--- | :--- |
| 需求名 | recall-gateway |
| 中文名 | Java 用户态召回 SSE 网关（Sa-Token 鉴权 + 用户状态 + dataset 权限校验，签发内部 JWT 转发 Python 内部召回流） |
| 来源 | `java-gateway-brief.md`（原文迁移）；Linear `LINK-35`；GitHub 跟踪 issue `ql-link/LinkRag-Service#31`；关联 Python 设计 issue `ql-link/LinkRag#90` 与 internal recall stream `.specs/recall-http-api/brief.md`；后续优化 `LINK-40` / `ql-link/LinkRag#96`（短期 token 直连 Python SSE） |
| 分支 | feature/recall-gateway（已从 `dev` 切出，2026-05-30） |
| 当前阶段 | 已建跟踪 issue #31 并附解决说明评论；已发 PR #30（→ dev，branch-pr-workflow，2026-05-30，https://github.com/ql-link/LinkRag-Service/pull/30）；质量审查通过、R1/R2 已修、全量 `mvn test` 117 绿、doc-sync/ai-links 通过；待 review/合并 |
| brief.md | 已冻结（2026-05-30，原文逐字节迁移自 `java-gateway-brief.md`，**正文未改动**；§8 开放问题的处置见下方「范围决策」与「brief 偏差登记」） |
| acceptance.feature | 已冻结（2026-05-30，27 Scenario / 含 3 Scenario Outline） |
| technical_design.md | 已审核通过（2026-05-30，14 节，27 Scenario 全映射） |
| implementation_report.md | 已生成（2026-05-30） |

## 范围决策（brief §8 五个开放问题敲定，2026-05-30 用户确认「按建议」）

| brief 开放问题 | 本需求处置 |
| :--- | :--- |
| ① 错误码风格冲突（项目数字码+中文 vs brief 英文串码） | 对前端 SSE `error.code` 用 brief §6 约定的**英文串码**（`UNAUTHORIZED` / `RECALL_SCOPE_FORBIDDEN` / `RECALL_INVALID_REQUEST` / `RECALL_INTERNAL_AUTH_FAILED` / `RECALL_ALL_SOURCES_FAILED` / `RECALL_TIMEOUT`）；项目内部沿用 `ErrorCode` 枚举（数字+中文）做异常体系与日志，新增召回号段并建立 `string ↔ enum` 映射。号段与映射在 TD 定。 |
| ② `datasetIds=[]` 语义（已定调，见偏差登记） | **空列表 = 搜当前用户自己的全部库**：Java 侧把空 `datasetIds` 展开为当前用户名下所有 dataset id 再召回；非空时照常校验全部归属当前用户。「全库」限定本人范围、无越权；Java 向 Python **永不传空 `dataset_ids`**（空输入已展开为具体 id）。边界：用户名下无 dataset 时返回空 `hits` 的 `recall_done`、不调 Python（待 acceptance 确认）。 |
| ③ 用户状态校验 | **复用现有登录态状态**（`SysUser.status==1` 为正常，见 `AuthServiceImpl`），召回前校验；非正常状态拒绝召回。不引入独立「召回准入」开关。映射错误码在 TD 定（倾向 `UNAUTHORIZED` / 403 语义）。 |
| ④ 限流方案与粒度 | 首版用 **guava `RateLimiter`（单机内存）**，按**登录用户**维度限流，默认阈值 **每用户每分钟 10 次**（配置化可调）；超限在建流前返回限流错误（429 语义）。不引入 Redis 分布式限流，后续按需演进。 |
| ⑤ Python 错误码透传 vs 映射 | 对 brief §4.3/§6 **已列出的已知码直接透传**给前端；未列出的意外 Python 错误 / HTTP 非 2xx **兜底为统一 `RECALL_*` 码**（不暴露内部堆栈）。不做全量映射表。 |

## brief 偏差登记（brief 正文按用户要求保持原样，以下为实现 override）

> 实现与 acceptance 以本节为准；brief 正文相关描述视为「后续演进目标」，首版不实现。

| brief 位置 | brief 原文 | 本需求 override（首版） |
| :--- | :--- | :--- |
| §3.1 字段约束 | `datasetIds` 空列表表示当前用户**被授权**全库召回 | 空列表 = **当前用户自己的全部库**（Java 展开为本人所有 dataset id）；「全库」限定本人范围，**无需独立授权**（搜自己的库不存在越权） |
| §4.2 内部 JWT | 空 `dataset_ids` 只允许在 Java 明确判断可全库召回时签发 | Java **永不向 Python 签发空 `dataset_ids`**：空输入已在 Java 侧展开为具体 id，JWT `dataset_ids` 与 body 一致且非空 |
| §7 验收要点 | `datasetIds=[]` 只有在 Java 明确授权全库召回时才允许 | `datasetIds=[]` 一律展开为本人全部库；本人无库时返回空 `hits`（待 acceptance 确认） |

说明：brief 的「全库」措辞（授权 / 越权）暗示系统级跨用户召回，但系统按 `userId` 归属 dataset、无跨用户机制；本需求将「全库」明确重定义为「当前用户自己的全部库」，消除越权问题。真正的系统级跨用户全库召回（需授权模型）不在本次范围，待后续 issue（可挂 `LINK-40`）。

## acceptance 覆盖

| 分类 | Scenario 数 |
| :--- | :--- |
| 一、建流前安全校验（不建流 / 不签发 JWT / 不调 Python；HTTP 错误） | 7 |
| 二、datasetIds 范围与「本人全部库」展开 | 3 |
| 三、内部调用契约（body 与 JWT 自洽，snake_case） | 5 |
| 四、成功结果转发（recall_done 最小候选） | 2 |
| 五、失败与错误映射（已建流 → SSE error + 关流） | 4 |
| 六、断连、超时与资源释放 | 4 |
| 七、不变量 | 2 |
| 合计（含 3 个 Scenario Outline：query 非法 / 多余字段 / Python 非 2xx 映射） | 27 |

## 阶段记录

- 2026-05-30 brief 迁移：将 `java-gateway-brief.md` 原文逐字节迁移至 `docs/recall-gateway/brief.md`（`diff` 校验一致，正文未改动）。核实仓库现状：Sa-Token / 数字 `ErrorCode` 体系 / dataset 归属校验（`DatasetServiceImpl.getOwnedDataset`）/ `SseEmitter` 用法（`DocumentParseSseServiceImpl`，MQ 驱动一对多广播）已存在；okhttp、jjwt 0.9.1 依赖在 pom 但**无现有用法**（流式 HTTP 客户端 + HS256 JWT 签发为净新增）；无限流组件（净新增）。
- 2026-05-30 **brief §8 五个开放问题敲定**（用户「按建议」确认）：①对前端英文串码 / 对内数字枚举 + 映射；②**首版不支持空 `datasetIds` 全库召回**（brief override，已登记）；③复用 `SysUser.status==1` 用户状态校验；④guava 单机 `RateLimiter` 按用户限流，默认 10 次/分钟（配置化）；⑤Python 已知错误码透传 + 意外错误兜底 `RECALL_*`。
- 2026-05-30 **brief 冻结**：开放问题已闭环（决策记录于本文件，brief 正文未改）。待进入 acceptance 阶段（acceptance-generator）。
- 2026-05-30 **决策②修订**（用户确认「搜本人全部库」，对已冻结决策的调整）：把 brief 模糊的「全库召回」明确为「空 `datasetIds` = 当前用户自己的全部库」——Java 侧展开为本人所有 dataset id 后召回，非空时照常校验归属。副作用（更安全）：Java 向 Python 永不传空 `dataset_ids`，消除 Python 端跨用户越权风险。系统级跨用户全库召回（需授权模型）不在本次范围。新增边界待 acceptance 明确：用户名下无 dataset 时返回空 `hits` 的 `recall_done`、不调 Python。
- 2026-05-30 **acceptance 首版生成**（acceptance-generator）：基于冻结 brief + 本文件决策/偏差，生成 27 个 Scenario（含 3 Outline：query 非法 / 多余字段拒绝 / Python 建流前非 2xx 映射），七大分类全覆盖 brief §7 九条验收点、§5 断连超时资源、§6 错误映射、§4 内部契约（body↔JWT 自洽、snake_case、HS256 claims）。重点把决策②（空 `datasetIds` 展开本人全部库、无库返回空 `hits` 不调 Python、永不向 Python 传空 `dataset_ids`）、建流前 HTTP 错误 vs 建流后 SSE `error` 的分界、对前端英文串码落为可断言 Then。待评审冻结。
- 2026-05-30 **acceptance 冻结**（用户确认）：27 个 Scenario（含 3 Outline）经确认通过，与冻结 brief + 决策/偏差对齐。从 `dev` 切出 `feature/recall-gateway` 分支。进入 technical_design 阶段（technical-design）。
- 2026-05-30 **technical_design 草稿**（technical-design）：基于真实代码定方案——新增 RecallController(SSE 端点)/RecallService/RecallScopeResolver/RecallRateLimiter(guava)/RecallUpstreamClient(okhttp 4.12 手动读流)/InternalJwtSigner(jjwt 0.9.1)/RecallProperties + 6 DTO；修改 ErrorCode(新增 30001 召回段)、GlobalExceptionHandler(补 HttpMessageNotReadableException→400)、application yml、api_contracts.md。关键技术点：建流前/后分界正好踩 Spring 异常边界（@SaCheckLogin/@Valid/BusinessException 在返回 SseEmitter 前→HTTP 错误；返回后→SSE error）；okhttp 无 okhttp-sse 用手动解析；upstream 抽象成接口便于 mock；guava RateLimiter+Cache 按用户。27 Scenario 全映射测试点。7 个待确认（JWT 密钥编码与 Python 对齐、建流前是否带英文码、Python hits 字段名、兜底码名 RECALL_UPSTREAM_ERROR、okhttp-sse 取舍、线程池阈值、status 语义）。待审核。
- 2026-05-30 **TD 审核通过**（用户 APPROVE）：待确认项按 TD 默认——兜底码 `RECALL_UPSTREAM_ERROR`、建流前数字码+status（英文码仅 SSE）；跨端项（JWT 密钥编码、Python `recall_done` 字段名）配置化预留、联调时与 Python 对齐。进入实现阶段（implementation-execution）。
- 2026-05-30 **实现完成**（implementation-execution）：按 TD §13 落地——link-model（ErrorCode 30001 段 / RecallSseError / 4 DTO + jackson-annotations）、link-core（手写 HS256 InternalJwtSigner / GlobalExceptionHandler 补 400）、link-service（RecallProperties / ScopeResolver / RateLimiter / OkHttpRecallUpstreamClient / RecallServiceImpl / RecallExecutorConfig + guava + okhttp）、link-api（RecallController）。8 处 TD 偏差（手写 JWT 避 jjwt 的 Java17 JAXB 问题、`@JsonAnySetter`+`@AssertTrue` 替代失效的 `@JsonIgnoreProperties` 拒未知字段、`canceledByClient` 标志区分超时与前端取消 等，详见 implementation_report.md）。全量 `mvn test` BUILD SUCCESS（含召回 6 测试类 26 用例，27 Scenario 全覆盖）；文档同步 7 篇 + .env.example，check_docs_sync/check_ai_links 通过。待质量审查（code-review-and-quality）。
- 2026-05-30 **质量审查 + 修复**（code-review-and-quality）：审查结论 REQUEST_CHANGES，2 个 Required + 4 个 Suggestion。已修两个 Required：**R1** SseEmitter 与 okhttp callTimeout 同为 stream-timeout-ms 形成等长计时器竞争（SseEmitter 先超时则静默关流、不发 `RECALL_TIMEOUT`，违反 Scenario 19）→ 新增 `emitter-timeout-buffer-ms`（默认 5000），emitter 超时 = stream-timeout-ms + 缓冲，保证上游超时先触发并发出 `RECALL_TIMEOUT`；**R2** guava `RateLimiter` 匀速实为「1 次/6 秒、不可突发」，与「每分钟 10 次」语义不符（用户改下 query 6 秒内重发即被 429）→ 用户选方案 (a)，改为**固定窗口计数**（窗口内允许突发到上限，超限拒）。顺手清理 **S2**（link-model/pom.xml `@JsonIgnoreProperties` 过期注释）。修复中全量回归捕获并修正一处自身回归：RateLimiter 加包级 2 参构造器后变双构造器，Spring 无法自动装配（所有 @SpringBootTest 上下文加载失败），给生产构造器加 `@Autowired` 解决。新增/调整测试 2 个（RateLimiter 突发+窗口滚动、ServiceImpl emitter 超时不变量）。全量 `mvn test` BUILD SUCCESS（117，0 失败）；check_docs_sync（45 文件，无问题）/check_ai_links 通过。S1（5 个未引用的数字 ErrorCode）/S3（JWT 同步签发空密钥→500）/S4（限流前置）为建议项，未做，留待后续。待提交 PR（branch-pr-workflow）。
- 2026-05-30 **发起 PR**（branch-pr-workflow）：`feature/recall-gateway` → `dev`，**PR #30**（https://github.com/ql-link/LinkRag-Service/pull/30）。提交 `feat(recall)` 含全部召回代码 + 28 测试 + 文档同步（43 文件），按「不混入无关本地修改」排除无关未跟踪目录 `docs/parse-retry-and-sparse-vector-java/`。PR 正文含 Summary/Changes/Tests/Risks（运行时风险：需配 `RAG_PYTHON_BASE_URL` + `RECALL_INTERNAL_JWT_SECRET`、网关关缓冲；跨端联调待对齐 JWT 密钥编码 + `recall_done` hits 字段名）。待 review/合并。
- 2026-05-30 **建跟踪 issue + 解决说明评论**：原 Linear `LINK-35` 在本 GitHub 仓库无对应 issue，按要求新建 `ql-link/LinkRag-Service#31`（需求/范围/验收/产物），并在其下评论说明解决方式（建流前后分界、内部 JWT 自洽、空 datasetIds 展开、固定窗口限流、超时缓冲、手写 HS256 取舍、质量门禁、跨端待联调），附 PR #30；PR #30 亦回链 issue #31。
