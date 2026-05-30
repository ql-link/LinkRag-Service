# 解析失败重试链路 + 审计字段适配（Java 端）验收契约
# 来源：ql-link/LinkRag-Service#16；brief.md 已冻结（v3，2026-05-30）。
#
# 契约前提（以 Python 权威源为准，合并 migration 0009）：
# - Python = 权威记账人，推进 document_parsed_log 与 document_parse_pipeline；Java = 任务投递人 + 传话人。
# - "重试" = 复用上次 Markdown 的阶段恢复（让 Python 从失败阶段含稀疏向量续跑），不是重新解析原文件。
# - 端到端终态权威单源 = document_parse_pipeline.pipeline_status，取值【大写】PENDING / PROCESSING / SUCCESS / FAILED；
#   pipeline_status=SUCCESS 仅在含稀疏向量在内的 6 阶段全成功后翻转。
# - document_parsed_log 已【删除】task_status、failure_reason 两列（migration 0007）；
#   task_status 仅作为 parse_result MQ 消息字段存在，取值【小写】success / failed，语义为端到端终态。
# - parse_task 重试字段：is_retry(bool, 默认 false) + previous_task_id(可空)；Python 以 is_retry 分流。
# - 判定数据源：document_parsed_log.parsed_object_key（是否已产出 Markdown）+ document_parse_pipeline.pipeline_status，
#   两表经 document_parsed_log.id = document_parse_pipeline.document_parsed_log_id（或共同 task_id）关联；判定走可写主库。
# - 验收由 JUnit/Mockito/MockMvc/SpringBootTest 承接，不引入 Cucumber。

Feature: Java 端解析失败重试消息与审计字段适配
  作为知识文件解析的业务方
  我希望 Java 能识别首次/重试/已成功，构造带溯源且复用 Markdown 的重试任务，并按新契约读审计字段与终态
  以便失败任务可做阶段恢复重试、已成功任务被友好拒绝、重试链可回溯，且不再依赖已删除的旧字段

  Background:
    Given 用户 alice 已登录
    And 文件 F1 属于 alice 且上传成功

  # ============================================================
  # 一、投递入口识别：首次 / 重试 / 已成功 / 运行中（issue 需求 1）
  # ============================================================

  Scenario: 无解析历史则判为首次解析
    Given 文件 F1 没有任何 document_parsed_log
    When alice 提交对 F1 的解析请求
    Then 判定为首次解析
    And 投递一条 parse_task 消息，is_retry=false 且 previous_task_id 为空
    And 生成新 task_id 并把 F1 的 latest_parse_task_id 更新为该 task_id

  Scenario: 上一轮在产出 Markdown 前失败则仍判为首次解析
    Given 文件 F1 最新任务的 parsed_object_key 为空
    And 其 document_parse_pipeline.pipeline_status=FAILED（Markdown 未产出即失败）
    When alice 提交对 F1 的解析请求
    Then 判定为首次解析而非阶段恢复重试
    And 投递的 parse_task 消息 is_retry=false 且 md_object_key 为本次新建路径

  Scenario: 已产出 Markdown 且流水线失败则判为可重试
    Given 文件 F1 最新失败任务 task_id=T-old
    And 该任务 parsed_object_key=K-old、parsed_bucket_name=rag-md
    And 其 document_parse_pipeline.pipeline_status=FAILED
    When alice 提交对 F1 的解析请求
    Then 判定为失败重试
    And 投递一条 parse_task 消息，is_retry=true 且 previous_task_id=T-old
    And 生成新 task_id=T-new 并把 latest_parse_task_id 更新为 T-new

  Scenario: 已成功任务的重新解析请求被友好拒绝且不发 MQ
    Given 文件 F1 最新任务 document_parse_pipeline.pipeline_status=SUCCESS
    When alice 提交对 F1 的解析请求
    Then 请求被友好拒绝并返回业务错误（提示已解析成功）
    And 不投递任何 parse_task 消息
    And 不生成新 task_id 且 latest_parse_task_id 不变

  Scenario Outline: 流水线运行中则拒绝重复提交且不发 MQ
    Given 文件 F1 最新任务 document_parse_pipeline.pipeline_status=<状态>
    When alice 提交对 F1 的解析请求
    Then 请求被拒绝（正在解析中，请勿重复提交）
    And 不投递任何 parse_task 消息

    Examples:
      | 状态       |
      | PENDING    |
      | PROCESSING |

  Scenario: 指针已设但流水线行尚未创建时视为运行中
    Given 文件 F1 的 latest_parse_task_id 已指向 T-new
    And 尚不存在 T-new 的 document_parsed_log 与 document_parse_pipeline（Python 未及写入）
    When alice 再次提交对 F1 的解析请求
    Then 请求被判为运行中并拒绝
    And 不投递任何 parse_task 消息

  # ============================================================
  # 二、重试消息构造与复用 Markdown 坐标（issue 需求 2）
  # ============================================================

  Scenario: 重试消息复用旧 Markdown 坐标且业务字段与原任务一致
    Given 文件 F1 失败任务 T-old：user_id=U1、dataset_id=D1、source_bucket=SB、source_object_key=SK、file_type=pdf
    And T-old 的 parsed_bucket_name=rag-md、parsed_object_key=K-old
    When 触发对 F1 的失败重试并生成 T-new
    Then parse_task 消息 task_id=T-new 且 T-new ≠ T-old
    And is_retry=true 且 previous_task_id=T-old
    And md_bucket=rag-md 且 md_object_key=K-old（复用旧产物，不新建路径）
    And user_id=U1、dataset_id=D1、source_bucket=SB、source_object_key=SK、file_type=pdf 与原任务一致

  Scenario: 首次解析消息保持向后兼容
    Given 文件 F2 为首次解析
    When 投递 F2 的 parse_task 消息
    Then is_retry=false（或省略）且 previous_task_id 为空
    And md_object_key 为本次新建路径（包含新 task_id）

  Scenario: 多轮重试时 previous_task_id 指向上一轮失败任务
    Given 文件 F1 的重试链为 T1(FAILED, 产物 K1) → T2(FAILED, 产物 K2)
    And F1 的 latest_parse_task_id=T2
    When 再次触发对 F1 的失败重试并生成 T3
    Then previous_task_id=T2（最近一次失败任务，而非 T1）
    And md_object_key=K2（最新一条 parsed_object_key 非空且 pipeline_status=FAILED 任务的产物）

  # ============================================================
  # 三、发送前完整性校验（issue 需求 2/3）
  # ============================================================

  Scenario Outline: 重试消息缺关键字段则不发送
    Given 一条待发送的重试 parse_task，is_retry=true
    And 字段 <字段> 为空
    When 执行发送前完整性校验
    Then 校验不通过且不投递任何 MQ 消息
    And 请求以错误结束，latest_parse_task_id 不被推进

    Examples:
      | 字段             |
      | previous_task_id |
      | md_bucket        |
      | md_object_key    |

  Scenario: 重试消息字段齐全则通过校验并发送
    Given 一条重试 parse_task：is_retry=true、previous_task_id=T-old、md_bucket=rag-md、md_object_key=K-old
    When 执行发送前完整性校验
    Then 校验通过并投递恰好一条 parse_task 消息

  # ============================================================
  # 四、parse_result 按新 task_id 处理（issue 需求 3，复用 #15 当前任务过滤）
  # ============================================================

  Scenario: 重试后按新 task_id 转发终态
    Given 文件 F1 重试后 latest_parse_task_id=T-new
    When Java 收到 task_id=T-new 的 parse_result 消息（task_status=success）且校验通过
    Then 向订阅 F1 的连接推送一条终态 SSE 事件
    And 不依据该消息回写任何业务表

  Scenario: 被取代的旧任务迟到结果不推终态
    Given 文件 F1 已重试，latest_parse_task_id=T-new，旧任务 T-old 已被取代
    When Java 收到 task_id=T-old 的迟到 parse_result 消息（task_status=failed）
    Then 不向 F1 推送任何终态 SSE 事件
    And 仅记录审计日志并提交跳过

  Scenario: Java 在任何分支都不依据 parse_result 回写业务表
    Given 任意一条 parse_result 消息（当前任务 / 旧任务 / 不匹配）
    When Java 消费该消息
    Then document_parsed_log、document_parse_pipeline 与解析产物字段都不被 Java 修改
    And Java 的动作仅限于校验、转发或补推 SSE、告警

  # ============================================================
  # 五、审计字段读取与重试链回溯查询（issue 需求 4/5）
  # ============================================================

  Scenario: DAO 能读取双向重试链审计字段
    Given T-new 的 document_parsed_log.retry_of_task_id=T-old
    And T-old 的 document_parse_pipeline.superseded_by_task_id=T-new
    When Java DAO 按 task_id 读取审计字段
    Then 能取到 T-new 的 retry_of_task_id=T-old
    And 能取到 T-old 的 superseded_by_task_id=T-new

  Scenario: 沿 retry_of_task_id 正常回溯整条重试链
    Given 重试链 T1 → T2 → T3，其中 retry_of_task_id：T3→T2、T2→T1、T1 为空
    When 按 task_id=T3 回溯重试链
    Then 返回链 [T3, T2, T1]
    And 在 retry_of_task_id 为空的 T1 处安全终止

  Scenario: 首次解析任务的重试链长度为一
    Given 任务 T1 的 retry_of_task_id 为空
    When 按 task_id=T1 回溯重试链
    Then 返回链 [T1]

  Scenario: 链断时回溯安全终止
    Given 任务 T3 的 retry_of_task_id=T-missing
    And 不存在 task_id=T-missing 的 document_parsed_log
    When 按 task_id=T3 回溯重试链
    Then 回溯在 T3 处安全终止（结果含 T3）
    And 不抛出异常

  Scenario: 超过深度上限时回溯被截断
    Given 重试链深度超过配置的回溯深度上限 N
    When 按链尾 task_id 回溯重试链
    Then 至多回溯 N 跳即停止
    And 不发生无限递归

  Scenario: 重试链成环时回溯即停
    Given retry_of_task_id 构成环（例如 A→B→A）
    When 从环上任一 task_id 回溯
    Then 检测到已访问过的 task_id 即终止
    And 不进入死循环

  # ============================================================
  # 六、契约对齐：终态判定迁移到 pipeline_status（brief §1.6 漂移收口）
  # ============================================================

  Scenario: Java 不再读取 document_parsed_log 的 task_status / failure_reason
    Given 运行库 document_parsed_log 已无 task_status、failure_reason 两列
    When Java 物化解析日志实体并执行入口判定、结果查询、卡住扫描与 parse_result 校验
    Then 生成的 SQL 不引用 task_status、failure_reason 列
    And 不发生 Unknown column 错误

  Scenario Outline: 结果列表前端态由 pipeline_status 推导
    Given 文件 F1 最新任务 document_parse_pipeline.pipeline_status=<pipeline>
    When alice 查询 F1 的解析结果
    Then 前端态为 <前端态>
    And 当 <pipeline>=FAILED 时失败原因取自 document_parse_pipeline.failure_reason

    Examples:
      | pipeline   | 前端态        |
      | PENDING    | parsing       |
      | PROCESSING | parsing       |
      | SUCCESS    | parse_success |
      | FAILED     | parse_failed  |

  Scenario Outline: 无流水线行时前端态由当前任务指针推导
    Given 文件 F1 不存在 document_parse_pipeline 行
    And F1 的 latest_parse_task_id <指针>
    When alice 查询 F1 的解析结果
    Then 前端态为 <前端态>

    Examples:
      | 指针   | 前端态        |
      | 已设置 | parsing       |
      | 为空   | parse_waiting |

  Scenario: 卡住扫描改用 pipeline_status 判定（回归 #15）
    Given 文件 F1 当前任务 document_parse_pipeline.pipeline_status=PROCESSING
    And 其 created_at 已超过该数据集阈值
    When 卡住扫描运行
    Then 命中卡住判定（等同原 task_status=created 语义）
    And 以 document_parse_pipeline 的 DB 终态为准补推或告警，沿用 #15 行为

  Scenario: Java 不再依赖 retry_count / last_retry_at
    Given document_parse_pipeline 已移除 retry_count、last_retry_at 两列
    When Java 执行入口判定、审计字段读取与重试链查询
    Then 任何查询都不引用 retry_count、last_retry_at
    And 解析流水线只读实体不映射这两列

  # ============================================================
  # 七、不变量
  # ============================================================

  Scenario: 库侧大写与消息侧小写状态取值不混用
    Given document_parse_pipeline.pipeline_status 取值为大写 SUCCESS / FAILED
    And parse_result 消息 task_status 取值为小写 success / failed
    When Java 在库侧判定终态或在消息侧处理 parse_result
    Then 库侧按大写枚举比较、消息侧按小写比较
    And 两套取值不互相混用
