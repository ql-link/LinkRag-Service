# Java 用户态召回 SSE 网关 验收契约
# 前提与约定（实现以本文件 + 冻结 brief + feature_info 的「范围决策/偏差登记」为准）：
#   - 接口：前端 POST /api/v1/recall/stream（satoken 登录态，Accept: text/event-stream）。
#   - 字段风格：Java 对前端用 camelCase（query / datasetIds / chunkId / docId / datasetId）；
#     Java 对 Python 用 snake_case（query / user_id / dataset_ids）。
#   - 建流前 vs 建流后分界（决定错误如何表达）：
#       · Java 自身校验（登录态 / 用户状态 / 参数 / 拒绝多余字段 / dataset 权限 / 限流）在“建立前端 SSE 流之前”完成；
#         失败用普通 HTTP 错误码返回，且不建流、不签发内部 JWT、不调用 Python。
#       · 校验全部通过后 Java 才建立前端 SSE 流（写响应头）并调用 Python；此后任何错误
#         （Python error 事件 / Python 非 2xx / 超时 / 内部 JWT 被拒）一律以 SSE error 事件表达，随后关闭前端 SSE。
#   - 前端错误码：SSE error.code 用英文串码（UNAUTHORIZED / RECALL_SCOPE_FORBIDDEN / RECALL_INVALID_REQUEST /
#     RECALL_INTERNAL_AUTH_FAILED / RECALL_ALL_SOURCES_FAILED / RECALL_TIMEOUT / 统一兜底 RECALL_ 前缀码）；message 不含内部堆栈。
#   - datasetIds 语义（已 override brief 正文）：空列表 = 当前用户自己的全部库（Java 展开为本人所有 dataset id）；
#     非空则要求全部归属当前用户；用户名下无库时直接返回空 hits 的 recall_done 且不调用 Python。Java 永不向 Python 发送空 dataset_ids。
#   - 内部 JWT：HS256 签发，claims 含 iss=tolink-java / aud=tolink-rag / sub=当前用户 / scope=recall:execute /
#     dataset_ids=已校验范围 / jti=request_id / 短有效期 exp。Java 不向 Python 透传前端 satoken。
#   - 首版不返回 chunk 正文、不在 Java 侧回查正文；Python 的 fused_score / scores / failed_sources 不进入前端 SSE。
#   - 限流：按登录用户，默认 10 次/分钟（配置化），超限在建流前返回限流（429 语义）。
#   - 验收由 JUnit / Mockito / MockMvc / SpringBootTest 承接，不引入 Cucumber；对 Python 的内部调用通过 mock/stub 上游 SSE 验证。

Feature: Java 用户态召回 SSE 网关
  作为已登录用户
  我希望通过 Java 发起流式召回
  以便 Java 先完成登录态 / 用户状态 / 数据集权限校验，再安全地调用 Python 召回并把结果以 SSE 转发给我

  Background:
    Given 用户 alice 已登录且状态正常（status==1）
    And alice 拥有数据集 DS1、DS2
    And Python 内部召回 stream 默认可用

  # ============================================================
  # 一、建流前安全校验（校验在建流前完成；失败用 HTTP 错误，不建流 / 不签发 JWT / 不调用 Python）
  # ============================================================

  Scenario: 未登录或 Sa-Token 无效时拒绝且不触达 Python
    Given 请求未携带有效登录态
    When 调用召回接口 query="问题" datasetIds=[DS1]
    Then 接口在建立 SSE 流之前返回未登录错误（401 语义，code=UNAUTHORIZED）
    And 不建立前端 SSE 流
    And 不签发内部 JWT
    And 不调用 Python 内部召回

  Scenario: 用户状态非正常时拒绝召回
    Given 用户 bob 已登录但其状态非正常（status != 1）
    When bob 调用召回接口 query="问题" datasetIds=[bob 拥有的数据集]
    Then 接口在建流前返回账号不可用错误（403 语义，code=UNAUTHORIZED）
    And 不调用 Python 内部召回

  Scenario: datasetIds 含不属于当前用户的数据集时按越权拒绝
    Given 数据集 DSX 不属于 alice
    When alice 调用召回接口 query="问题" datasetIds=[DS1, DSX]
    Then 接口在建流前返回越权错误（403 语义，code=RECALL_SCOPE_FORBIDDEN）
    And 不建立前端 SSE 流
    And 不签发内部 JWT
    And 不调用 Python 内部召回

  Scenario Outline: query 非法时在建流前返回参数错误
    When alice 调用召回接口 query=<query> datasetIds=[DS1]
    Then 接口在建流前返回参数错误（400/422 语义，code=RECALL_INVALID_REQUEST）
    And 不调用 Python 内部召回

    Examples:
      | query          |
      | 空字符串        |
      | 纯空白字符串    |
      | 缺失 query 字段 |
      | query 非字符串类型 |

  Scenario Outline: 传入首版不接收的字段时返回参数错误
    When alice 调用召回接口 query="问题" datasetIds=[DS1] 且附带额外字段 <字段>
    Then 接口在建流前返回参数错误（code=RECALL_INVALID_REQUEST）
    And 不调用 Python 内部召回

    Examples:
      | 字段           |
      | docIds         |
      | topK           |
      | sources        |
      | strict         |
      | includeContent |

  Scenario: 同一用户召回频率超过限流阈值时在建流前被拒
    Given alice 在当前时间窗内已发起达到上限（默认 10 次/分钟）的召回
    When alice 再次调用召回接口
    Then 接口在建流前返回限流错误（429 语义）
    And 不建立前端 SSE 流
    And 不调用 Python 内部召回

  Scenario: 限流按用户独立计数
    Given alice 已达本时间窗召回上限
    And 用户 carol 本时间窗内尚未召回
    When carol 调用合法召回接口
    Then carol 的请求不被限流
    And carol 的请求进入正常召回流程

  # ============================================================
  # 二、datasetIds 范围与「本人全部库」展开（决策② override brief）
  # ============================================================

  Scenario: 非空 datasetIds 全部归属当前用户时按该范围召回
    When alice 调用召回接口 query="问题" datasetIds=[DS1, DS2]
    Then 校验通过并调用 Python 内部召回
    And 发往 Python 的 dataset_ids == [DS1, DS2]

  Scenario: 空 datasetIds 展开为当前用户的全部数据集
    Given alice 名下数据集为 [DS1, DS2]
    When alice 调用召回接口 query="问题" datasetIds=[]
    Then 校验通过并调用 Python 内部召回
    And 发往 Python 的 dataset_ids == [DS1, DS2]
    And 发往 Python 的 dataset_ids 非空

  Scenario: 用户名下没有任何数据集时空 datasetIds 直接返回空结果
    Given 用户 dave 已登录且名下没有任何数据集
    When dave 调用召回接口 query="问题" datasetIds=[]
    Then 前端收到 event: recall_done 且 hits 为空
    And 不调用 Python 内部召回
    And 随后关闭前端 SSE

  # ============================================================
  # 三、内部调用契约：body 与 JWT 自洽（Java→Python）
  # ============================================================

  Scenario: 调用 Python 时请求体使用 snake_case 且不透传前端 satoken
    When alice 调用召回接口 query="问题" datasetIds=[DS1, DS2]
    Then 发往 Python 的请求体字段为 query、user_id、dataset_ids（snake_case）
    And 请求头包含 Authorization: Bearer <内部JWT> 与 X-Request-Id
    And 请求不包含前端 satoken 头

  Scenario: 发往 Python 的 user_id 与内部 JWT 的 sub 一致
    Given alice 的用户 ID 为 U
    When alice 调用召回接口
    Then 发往 Python 的 body.user_id == U
    And 内部 JWT 的 sub == "U"
    And 二者一致

  Scenario: 发往 Python 的 dataset_ids 与内部 JWT 的 dataset_ids 一致
    When alice 调用召回接口 datasetIds=[DS1, DS2]
    Then 发往 Python 的 body.dataset_ids == [DS1, DS2]
    And 内部 JWT 的 dataset_ids == [DS1, DS2]
    And 二者一致

  Scenario: 内部 JWT 使用 HS256 且包含约定 claims
    Given 本次请求的 request_id 为 R
    When alice 调用召回接口
    Then 内部 JWT 使用 HS256 算法签发
    And claims 含 iss=tolink-java、aud=tolink-rag、scope=recall:execute
    And claims.jti == R
    And claims.exp 为短有效期且由配置项控制

  Scenario: 空 datasetIds 展开后 JWT 与 body 的 dataset_ids 仍一致且非空
    Given alice 名下数据集为 [DS1, DS2]
    When alice 调用召回接口 datasetIds=[]
    Then 发往 Python 的 body.dataset_ids == [DS1, DS2]
    And 内部 JWT 的 dataset_ids == [DS1, DS2]
    And 二者一致且非空

  # ============================================================
  # 四、成功结果转发（recall_done，最小候选，保持顺序，发后关流）
  # ============================================================

  Scenario: Python 返回 recall_done 时向前端输出最小候选并关流
    Given alice 已通过校验并建立前端 SSE 流
    When Python 返回 recall_done，hits 含 chunk_id/doc_id/dataset_id/fused_score/scores 且 failed_sources=[]
    Then 前端收到 event: recall_done
    And 前端 hits 每项仅含 chunkId、docId、datasetId（camelCase）
    And 前端 hits 不含 fused_score、scores、failed_sources
    And 前端 hits 顺序与 Python 返回顺序一致
    And 前端 hits 不含 chunk 正文
    And 随后关闭前端 SSE

  Scenario: Python 的降级元信息不进入前端事件
    Given alice 已建立前端 SSE 流
    When Python 返回 recall_done 且 failed_sources 非空（部分召回路失败）
    Then 前端仍只收到 recall_done 与最小候选 hits
    And 前端事件不包含 failed_sources（Java 可记录日志/指标，不影响前端契约）

  # ============================================================
  # 五、失败与错误映射（已建流 → 统一 SSE error + 关流，不暴露堆栈）
  # ============================================================

  Scenario: Python 返回全路失败 error 时透传错误码
    Given alice 已建立前端 SSE 流
    When Python 返回 event: error 且 code=RECALL_ALL_SOURCES_FAILED
    Then 前端收到 event: error 且 code=RECALL_ALL_SOURCES_FAILED
    And error.message 不含内部堆栈
    And 随后关闭前端 SSE

  Scenario: 对 Python 的调用超时映射为 RECALL_TIMEOUT
    Given alice 已建立前端 SSE 流
    When 对 Python 的调用超过整体超时阈值（RECALL_STREAM_TIMEOUT_MS）仍无完整结果
    Then 取消对 Python 的调用
    And 前端收到 event: error 且 code=RECALL_TIMEOUT
    And 随后关闭前端 SSE

  Scenario Outline: Python 建流前 HTTP 非 2xx 统一映射为前端 error 事件
    Given alice 已建立前端 SSE 流
    When Python 在建立上游 stream 前以 HTTP <status> 响应
    Then 前端收到 event: error 且 code=<错误码>
    And error.message 不含内部堆栈
    And 记录 Python 的 HTTP status 与 request_id
    And 随后关闭前端 SSE

    Examples:
      | status | 错误码                       |
      | 401    | RECALL_INTERNAL_AUTH_FAILED  |
      | 403    | RECALL_INTERNAL_AUTH_FAILED  |
      | 504    | RECALL_TIMEOUT               |
      | 500    | 统一兜底 RECALL_ 前缀码        |
      | 502    | 统一兜底 RECALL_ 前缀码        |

  Scenario: Python 返回契约外的未知错误时兜底且不暴露堆栈
    Given alice 已建立前端 SSE 流
    When Python 返回未在契约内的错误（未知 code 或异常响应体）
    Then 前端收到 event: error 且 code 为统一兜底错误码（RECALL_ 前缀）
    And error.message 不含内部堆栈与原始异常信息
    And 随后关闭前端 SSE

  # ============================================================
  # 六、断连、超时与资源释放
  # ============================================================

  Scenario: 前端断开 SSE 时取消对 Python 的调用
    Given alice 已建立前端 SSE 流且 Java 正在读取 Python 上游 stream
    When 前端主动断开与 Java 的 SSE 连接
    Then Java 关闭/取消到 Python 的内部 stream
    And 不将该断连记为业务错误
    And 记录一条审计日志

  Scenario: Python stream 结束后关闭前端 SSE
    Given alice 已建立前端 SSE 流
    When Python 上游 stream 正常结束或被关闭
    Then Java 关闭前端 SSE 连接
    And 不残留到 Python 的连接资源

  Scenario: 召回响应头声明 SSE 并禁用缓存与缓冲
    When alice 通过校验并建立前端 SSE 流
    Then 响应 Content-Type 为 text/event-stream
    And 响应包含 Cache-Control: no-cache
    And 响应缓冲被关闭（事件可即时下发）

  Scenario: 对 Python 的调用设置连接 / 读取 / 整体超时
    When Java 调用 Python 内部召回
    Then 设置了连接超时、读取超时与整体执行超时
    And 整体超时取配置项 RECALL_STREAM_TIMEOUT_MS（默认 60000 毫秒）

  # ============================================================
  # 七、不变量（贯穿召回链路）
  # ============================================================

  Scenario: 任何建流前校验失败都不触达 Python 也不签发 JWT
    Given 召回请求在建流前校验阶段失败（未登录 / 状态异常 / 越权 / 参数非法 / 多余字段 / 限流 任一）
    When 接口返回 HTTP 错误
    Then 不建立前端 SSE 流
    And 不签发内部 JWT
    And 不调用 Python 内部召回

  Scenario: Java 永不向 Python 发送空 dataset_ids
    Given 任意一次通过校验并进入 Python 调用的召回
    When Java 构造发往 Python 的请求
    Then body.dataset_ids 非空
    And 内部 JWT 的 dataset_ids 非空
