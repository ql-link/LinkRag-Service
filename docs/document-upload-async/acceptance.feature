# 文档文件上传异步化（线程池接入）验收契约
# 前提：
#   - uploadStatus 取值仅：uploading / success / failed。
#   - 同步阶段（请求线程）只做鉴权/校验/物化临时文件/落 uploading/立即返回；
#     success/failed 一律由异步上传任务或 uploading 超时扫描写入。
#   - 异步化只省「Java→OSS」段；浏览器→Java 请求体接收时间不变（非功能点，不断言）。
#   - 前端按 uploadStatus 轮询 list/detail 为跨端依赖，本契约只断言 Java 端可观测行为。
#   - 数据库存在唯一约束 uk_dataset_user_name_suffix (dataset_id, user_id, original_filename, file_suffix)。
#   - 验收由 JUnit/Mockito/MockMvc/SpringBootTest 承接，不引入 Cucumber。

Feature: 文档文件上传异步化
  作为已登录用户
  我希望上传文档后接口立即返回 uploading 状态
  以便不被 OSS 上传阻塞，由后台异步完成上传与状态回写，并能在失败后原名重试

  Background:
    Given 用户 alice 已登录
    And alice 拥有数据集 DS1

  # ============================================================
  # 一、同步快速失败（请求线程内即时返回，不落 uploading 记录）
  # ============================================================

  Scenario: 未登录上传被拒
    Given 请求未携带有效登录态
    When 调用上传接口向 DS1 上传文件 "a.pdf"
    Then 接口同步返回未登录错误（401 语义）
    And 不创建任何文件记录
    And 未物化临时文件且未调用 OSS 上传

  Scenario: 数据集不存在或无权访问
    When alice 向不属于自己的数据集 DSX 上传文件 "a.pdf"
    Then 接口同步返回 404
    And 不创建任何文件记录
    And 未物化临时文件且未调用 OSS 上传

  Scenario Outline: 文件基础校验不通过则同步返回 400 且不落记录
    When alice 向 DS1 上传 <情形>
    Then 接口同步返回 400
    And 不创建 uploading 记录
    And 未物化临时文件且未调用 OSS 上传
    And 未投递解析任务

    Examples:
      | 情形                       |
      | 空文件                     |
      | 不支持的后缀（如 .exe）     |
      | 超过大小上限的文件         |
      | 文件名含非法字符           |

  # ============================================================
  # 二、异步上传主流程（落 uploading 立即返回 → 异步回写 success → 解析投递）
  # ============================================================

  Scenario: 校验通过后落 uploading 并立即返回
    Given alice 上传合法且无同名的文件 "a.pdf" 到 DS1
    When 上传接口完成同步阶段
    Then 创建文件记录 F1 且 F1.uploadStatus == uploading
    And 接口立即返回 F1 且其 uploadStatus == uploading
    And 此时 F1.objectKey 与 F1.fileUrl 为空
    And OSS 上传不在请求线程内完成（已提交至 document-upload 线程池）

  Scenario: 异步 OSS 上传成功后回写 success
    Given 文件 F1 处于 uploading 且其上传任务已提交线程池
    When 异步任务的 OSS 上传成功并返回对象位置
    Then F1.uploadStatus 变为 success
    And F1.objectKey 与 F1.fileUrl 非空
    And F1.isUploadSuccess == true
    And 该次上传的临时文件被清理

  Scenario: parseImmediately=true 时在上传成功之后才投递解析
    Given alice 以 parseImmediately=true 上传合法文件 "a.pdf" 到 DS1
    When 异步 OSS 上传成功
    Then 解析任务 MQ 消息在上传成功之后被投递一次
    And 在 uploading 阶段（上传成功前）不投递任何解析任务

  Scenario: parseImmediately=false 时不投递解析
    Given alice 以 parseImmediately=false 上传合法文件 "a.pdf" 到 DS1
    When 异步 OSS 上传成功
    Then F1.uploadStatus == success
    And 不投递任何解析任务 MQ 消息

  # ============================================================
  # 三、异步失败与背压（失败落 failed，不抛 HTTP 错误，由轮询发现）
  # ============================================================

  Scenario: OSS 上传失败落 failed 且不影响接口返回
    Given 文件 F1 处于 uploading
    When 异步 OSS 上传失败（返回空或抛异常）
    Then F1.uploadStatus 变为 failed
    And F1.failureReason 非空
    And 不投递解析任务
    And 该次上传的临时文件被清理
    And 该失败不表现为上传接口的 HTTP 错误（接口此前已返回 uploading）

  Scenario: 线程池与队列均满时拒绝并标记 failed（不退回同步）
    Given document-upload 线程池核心线程与等待队列均已占满
    When alice 上传合法文件触发提交线程池被拒绝
    Then 该文件记录被置为 failed
    And failureReason 提示服务繁忙可稍后重试
    And 不在请求线程内同步执行该次 OSS 上传（不退回 CallerRuns 同步行为）
    And 该次上传的临时文件被清理

  # ============================================================
  # 四、在途上传持久性兜底（uploading 超时扫描 + 启动清理）
  # ============================================================

  Scenario Outline: uploading 超时扫描把超阈值仍 uploading 的记录置 failed
    Given 文件 <file> 处于 uploading 且创建时间距今 <已用时长>
    When uploading 超时扫描以阈值 <阈值> 运行
    Then 该记录是否被置为 failed 为 <置failed>

    Examples:
      | file | 阈值     | 已用时长   | 置failed |
      | F1   | 默认阈值 | 超过阈值   | 是       |
      | F2   | 默认阈值 | 未超过阈值 | 否       |

  Scenario: 被超时扫描置 failed 的记录写入可读 failureReason
    Given 文件 F1 处于 uploading 且已超过超时阈值
    When uploading 超时扫描运行
    Then F1.uploadStatus 变为 failed
    And F1.failureReason 提示上传超时可重试

  Scenario: 应用启动时清理残留上传临时文件
    Given 本地临时目录存在上次进程异常退出遗留的上传临时文件
    When 应用启动清理逻辑运行
    Then 这些残留临时文件被删除

  # ============================================================
  # 五、同名重试（撞 failed 复用旧行，撞 uploading/success 拦截）
  # ============================================================

  Scenario: 同名且旧记录为 failed 则复用该行重置为 uploading
    Given DS1 下已存在文件 "a.pdf" 且其 uploadStatus == failed
    When alice 再次上传同名文件 "a.pdf"
    Then 不新增文件记录（复用原 failed 行的同一记录）
    And 该记录 uploadStatus 重置为 uploading
    And 该记录 failureReason 被清空
    And 重新进入异步上传流程

  Scenario: 复用旧行不触发唯一约束冲突
    Given DS1 下已存在文件 "a.pdf" 且其 uploadStatus == failed
    When alice 原名重试 "a.pdf"
    Then 通过复用同一行完成，不执行新的 insert
    And 不因唯一约束 uk_dataset_user_name_suffix 报重名或冲突错误

  Scenario Outline: 同名且旧记录非 failed 则同步拦截
    Given DS1 下已存在文件 "a.pdf" 且其 uploadStatus == <状态>
    When alice 再次上传同名文件 "a.pdf"
    Then 接口同步返回 400（已存在同名文件）
    And 不创建新记录
    And 不复用也不重置原记录
    And 未调用 OSS 上传

    Examples:
      | 状态      |
      | uploading |
      | success   |

  # ============================================================
  # 六、孤儿对象兜底（OSS 成功但 DB 回写失败 → 打日志留痕）
  # ============================================================

  Scenario: OSS 上传成功但 DB 回写失败时打告警日志留痕
    Given 文件 F1 处于 uploading
    When 异步任务的 OSS 上传成功但随后 DB 回写 success 失败
    Then 输出一条告警日志且日志内容包含该对象的 objectKey
    And F1 仍保持 uploading（随后由 uploading 超时扫描置 failed）
    And 首版不对已上传的 OSS 对象执行补偿删除

  # ============================================================
  # 七、线程池配置（多池就绪：@ConfigurationProperties + 独立专用池 + env 覆盖）
  # ============================================================

  Scenario: document-upload 使用独立命名的专用线程池（多池就绪）
    Given thread-pool 配置下存在 document-upload 池配置段
    When 应用启动
    Then 创建专用线程池 bean documentUploadExecutor 且线程名前缀为 document-file-upload-
    And 该池为独立专用池，不作为兜底所有异步任务的通用池
    And 不存在通用 customThreadPool bean

  Scenario: 改 @ConfigurationProperties 后按嵌套 key 绑定 document-upload 池
    Given thread-pool.document-upload 配置段含 core-pool-size/max-pool-size/queue-capacity/keep-alive-seconds/thread-name-prefix
    When 应用以默认配置启动
    Then document-upload 线程池按这些配置成功初始化

  Scenario Outline: 环境变量覆盖 document-upload 池参数仍生效
    Given 设置环境变量 <env>=<值>
    When 应用启动并绑定 thread-pool.document-upload 配置
    Then 配置项 <配置项> 的生效值为 <值>

    Examples:
      | env                            | 值      | 配置项             |
      | THREAD_POOL_CORE_SIZE          | 10      | core-pool-size     |
      | THREAD_POOL_MAX_SIZE           | 20      | max-pool-size      |
      | THREAD_POOL_QUEUE_CAPACITY     | 100     | queue-capacity     |
      | THREAD_POOL_KEEP_ALIVE_SECONDS | 120     | keep-alive-seconds |
      | THREAD_POOL_THREAD_NAME_PREFIX | doc-up- | thread-name-prefix |

  Scenario: document-upload 池配置非法时启动校验失败
    Given thread-pool.document-upload 配置 max-pool-size 小于 core-pool-size
    When 应用启动绑定并校验该池配置
    Then 启动失败并提示配置校验错误

  # ============================================================
  # 八、不变量（贯穿上传链路）
  # ============================================================

  Scenario: 上传终态只由异步任务或兜底扫描写入
    Given 任意一次通过同步校验的上传
    When 经历同步阶段与异步阶段
    Then 请求线程只把记录推进到 uploading
    And success 或 failed 仅由异步上传任务或 uploading 超时扫描写入

  Scenario: 同步阶段失败不残留任何在途产物
    Given 上传在同步校验阶段失败（未登录/无权/格式/大小/文件名/同名非 failed 任一）
    When 接口返回错误
    Then 不存在该次上传产生的 uploading 记录
    And 未调用 OSS 上传
    And 未投递解析任务
    And 未残留该次上传的临时文件
