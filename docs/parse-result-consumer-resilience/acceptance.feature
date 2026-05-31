# parse_result 消费接收兜底验收契约
# 前提：Python 是权威记账人（先写 document_parsed_log.task_status，再发 tolink.rag.parse_result）；
#       Java 是传话人（只校验已持久化终态并转发 SSE，从不依据 parse_result 回写业务状态）。
# task_status 取值仅：created / success / failed。
# 验收由 JUnit/Mockito/MockMvc/SpringBootTest 承接，不引入 Cucumber。

Feature: Java 端 parse_result 消费的接收兜底
  作为知识文件解析的业务方
  我希望 Java 消费 parse_result 时具备坏消息隔离、当前任务过滤与卡住兜底
  以便坏消息不空转、瞬时故障不丢消息、旧任务不误导前端、上游卡住可被发现

  # ============================================================
  # 一、ACK 治理：失败分类与重试（专用容器工厂 + 错误处理器）
  # ============================================================

  Scenario: 归属不匹配的消息不重试并跳过
    Given document_parsed_log 已存在 task_id=T1 且 task_status=success
    When Java 收到 parse_result 消息 task_id=T1 但 original_file_id 与该日志不一致
    Then 该消息被判定为业务不可恢复
    And 不进入重试
    And 输出告警日志并记监控指标
    And 该消息被提交跳过（offset 前进）
    And 不发布任何 SSE 事件

  Scenario Outline: 消息与已持久化终态不一致一律判为不可恢复
    Given document_parsed_log 已存在 task_id=T1 且 task_status=success
    When Java 收到 parse_result 消息 task_id=T1 但 <字段> 与该日志不一致
    Then 该消息被判定为业务不可恢复并被提交跳过
    And 不进入重试
    And 不发布任何 SSE 事件

    Examples:
      | 字段        |
      | task_status |
      | dataset_id  |
      | user_id     |

  Scenario: 坏消息不阻塞后续正常消息
    Given 队列中先有一条业务不可恢复消息，后有一条与日志完全匹配的当前任务终态消息
    When parse_result 消费者依次处理两条消息
    Then 第一条被告警并提交跳过
    And 第二条被正常处理并发布终态 SSE 事件

  Scenario: 日志暂不存在时带退避重试且最终跳过
    Given parse_result 消息 task_id=T2 到达
    And document_parsed_log 中查不到 task_id=T2
    When 消费者处理该消息
    Then 消费者对该消息按退避策略重试有限次
    And 重试间隔大于默认零退避（不在毫秒内连续耗尽）
    And 重试期间若日志仍不存在则告警并提交跳过
    And 不发布任何 SSE 事件

  Scenario: 日志在重试窗口内出现则正常处理
    Given parse_result 消息 task_id=T2 到达时 document_parsed_log 暂无 task_id=T2
    And 在重试窗口内 Python 写入了 task_id=T2 的终态日志
    When 消费者重试该消息
    Then 校验通过
    And 发布该任务的终态 SSE 事件

  Scenario: 基础设施异常带退避重试，耗尽后告警跳过
    Given parse_result 消息 task_id=T3 与日志匹配
    When 处理过程中持续发生读库失败
    Then 消费者按退避策略重试有限次
    And 重试耗尽后输出告警日志并记监控指标
    And 该消息被提交跳过
    And 不引入死信队列

  Scenario: 重试是幂等的
    Given parse_result 消息 task_id=T1 与当前任务终态日志匹配
    When 该消息因基础设施抖动被处理多次
    Then 每次处理只做校验与转发 SSE
    And 不产生除重复 SSE 推送外的任何副作用
    And 业务持久化状态不被 Java 改写

  Scenario: 缓存补偿消费链路不受本次改动影响
    Given parse_result 使用其专用容器工厂与错误处理器
    When tolink.cache.evict 的缓存补偿消息到达
    Then 缓存补偿仍走默认容器工厂与默认错误处理
    And 其消费行为与本次改动前一致

  # ============================================================
  # 二、SSE 当前任务过滤（旧任务/乱序不误导前端）
  # ============================================================

  Scenario: 当前任务的终态正常推送
    Given 文件 F1 的 latest_parse_task_id=T-new
    And document_parsed_log 中 task_id=T-new 终态为 success
    When Java 收到 task_id=T-new 的 parse_result 消息且校验通过
    Then 向订阅 F1 的连接推送一条终态 SSE 事件
    And 事件状态对应 success

  Scenario: 旧任务迟到结果不推终态且记审计
    Given 文件 F1 已重试，latest_parse_task_id=T-new
    And 收到属于旧任务 task_id=T-old 的 parse_result 消息（failed）
    When Java 处理该消息且归属校验通过
    Then 不向 F1 推送 parse_failed 终态事件
    And 记录一条审计日志说明旧任务结果被忽略
    And 该消息被提交（不重试）

  Scenario: 当前任务指针缺失时放行并记审计
    Given 文件 F1 的 latest_parse_task_id 为空（历史数据）
    When Java 收到该文件某任务的 parse_result 消息且校验通过
    Then 默认放行并推送终态 SSE 事件（fail-open）
    And 记录一条审计日志说明因指针缺失无法判定当前任务

  # ============================================================
  # 三、卡住任务扫描 + 以 DB 为准补推
  # ============================================================

  Scenario: 未超阈值的进行中任务不告警不补推
    Given 文件 F1 当前任务 task_status=created
    And 其 created_at 距今未超过该数据集阈值
    When 卡住扫描运行
    Then 不输出告警
    And 不补推 SSE

  Scenario: 超阈值且 DB 已终态则以 DB 为准补推
    Given 文件 F1 当前任务 task_id=T-new，created_at 已超过该数据集阈值
    And 重读 document_parsed_log 时 task_id=T-new 已为 success
    When 卡住扫描运行
    Then 以 DB 终态补推一次该任务的终态 SSE 事件
    And 补推经当前任务过滤且对前端幂等

  Scenario: 超阈值且 DB 仍为 created 则只告警不补推
    Given 文件 F1 当前任务 task_id=T-new，created_at 已超过该数据集阈值
    And 重读 document_parsed_log 时 task_id=T-new 仍为 created
    When 卡住扫描运行
    Then 输出告警日志并记监控指标
    And 不补推 SSE

  Scenario: 告警内容可回查 DB
    Given 卡住扫描命中一个仍为 created 的超阈值任务
    When 输出告警
    Then 告警包含 task_id、original_file_id、document_parsed_log_id、dataset_id、创建时间、超时时长与环境

  Scenario Outline: 超时阈值按数据集生效，缺省回落默认 5 分钟
    Given 数据集 <dataset> 的阈值配置为 <阈值>
    And 该数据集下文件 <file> 当前任务 task_status=created，已卡 <已用时长>
    When 卡住扫描运行
    Then 是否命中告警为 <命中>

    Examples:
      | dataset | 阈值     | file | 已用时长 | 命中 |
      | DS-默认 | 未配置   | F1   | 6 分钟   | 是   |
      | DS-默认 | 未配置   | F2   | 3 分钟   | 否   |
      | DS-长   | 20 分钟  | F3   | 6 分钟   | 否   |
      | DS-长   | 20 分钟  | F4   | 25 分钟  | 是   |

  # ============================================================
  # 四、不变量（贯穿三块工作）
  # ============================================================

  Scenario: Java 在任何分支都不回写业务终态
    Given 任意一条 parse_result 消息（匹配/不匹配/旧任务/重试）
    When Java 消费该消息
    Then document_parsed_log 与解析产物字段不被 Java 修改
    And Java 的动作仅限于校验、转发或补推 SSE、告警
