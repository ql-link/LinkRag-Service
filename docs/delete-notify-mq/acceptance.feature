# 删除通知 MQ（Java producer 落地）验收契约
# 范围：issue ql-link/LinkRag-Service#29 第 1 部分（删除通知 Java 半）。
# 前提：
#   - 承接 #27 / PR #28：删除已是隐性删除（软删保留原文件）；afterCommit 发送时机已就位，本需求把占位升级为真实投递。
#   - 本需求只验收 Java 侧「通知按契约正确投递 / 回滚不发 / 失败不影响删除」；Python 侧消费与端到端联调在另一仓库，不在本验收范围。
#   - 删除通知契约：topic = tolink.rag.document_delete，点对点队列（与 parse_task 一致，非广播），扁平 JSON、字段 snake_case。
#   - 按删除范围分流（delete_type）：
#       delete_type=dataset → 带 dataset_id + user_id，不含 original_file_id（Python 按 dataset_id 删名下全部衍生产物）。
#       delete_type=file    → 带 original_file_id + dataset_id + user_id（Python 按 original_file_id 删该文件衍生产物）。
#   - 可靠性：尽力发；afterCommit 发送失败 → 内部告警/留痕并吞掉，绝不外抛、不影响已提交的删除；无 DLQ、无对账兜底。
#   - 幂等天然成立（Python 按 id 删，删二次 no-op）；Java 侧不加 trace/去重字段，载荷保持最简。
#   - 发送前完整性校验（仿 parse_task）：delete_type 合法、dataset_id/user_id 非空、file 范围 original_file_id 非空，缺字段不投递。
#   - 删除接口对外形态（HTTP 返回/入参/URL）不变。
#   - 验收由 JUnit/Mockito/MockMvc/SpringBootTest 承接，不引入 Cucumber；断言均可机器验证
#     （如 verify(发送器).send(满足字段约定的消息)、回滚/未删路径 verify(发送器, never())、发送抛异常时删除仍成功返回）。

Feature: 删除链路 MQ 通知（Java 向 Python 投递删除通知）
  作为 Java 管理端
  我希望在数据集/文件删除提交后，按删除范围向 Python 投递一条删除通知
  以便 Python 据此清理其侧衍生产物，且通知失败绝不影响已完成的删除

  Background:
    Given 用户 alice 已登录
    And alice 拥有数据集 DS1

  # ============================================================
  # 一、删除通知投递主流程（按删除范围分流）
  # ============================================================

  Scenario: 删除单个文件在事务提交后投递 file 范围删除通知
    Given DS1 下存在文件 F1
    When alice 删除文件 F1 且删除事务成功提交
    Then 在事务提交后（afterCommit）向 topic "tolink.rag.document_delete" 投递且仅投递一条删除通知
    And 该消息 delete_type == "file"
    And 该消息 original_file_id == F1
    And 该消息 dataset_id == DS1
    And 该消息 user_id == alice

  Scenario: 删除数据集在事务提交后投递 dataset 范围删除通知
    Given DS1 下存在文件 F1
    When alice 删除数据集 DS1 且删除事务成功提交
    Then 在事务提交后（afterCommit）向 topic "tolink.rag.document_delete" 投递且仅投递一条删除通知
    And 该消息 delete_type == "dataset"
    And 该消息 dataset_id == DS1
    And 该消息 user_id == alice
    And 该消息不含 original_file_id 字段

  Scenario: 删除含多文件的数据集仍只投递一条 dataset 范围通知（不下发文件 id 列表）
    Given DS1 下存在文件 F1、F2、F3
    When alice 删除数据集 DS1 且删除事务成功提交
    Then 仅投递一条 delete_type == "dataset" 的删除通知
    And 该消息以 dataset_id == DS1 标识范围
    And 该消息不含任何 original_file_id 或其列表（无论名下文件多少，消息体大小恒定）

  # ============================================================
  # 二、消息契约形态（不变量）
  # ============================================================

  Scenario Outline: 删除通知消息形态与最简载荷（两种范围一致的不变量）
    Given DS1 下存在文件 F1
    When alice 执行 <删除动作> 且事务成功提交
    Then 投递的删除通知为点对点队列消息（与 parse_task 一致，非广播）
    And 消息体为扁平 JSON 且字段名均为 snake_case
    And 消息恰好包含约定字段集合 <字段集合>
    And 消息不含任何去重 / 追踪（trace）等额外字段

    Examples:
      | 删除动作      | 字段集合                                          |
      | 删除文件 F1   | delete_type, original_file_id, dataset_id, user_id |
      | 删除数据集 DS1 | delete_type, dataset_id, user_id                  |

  # ============================================================
  # 三、afterCommit 时机（回滚不发）
  # ============================================================

  Scenario Outline: 删除事务回滚则不投递删除通知且删除未生效
    Given alice 执行 <删除动作> 的过程在事务内因异常回滚
    When 事务结束
    Then 不向 "tolink.rag.document_delete" 投递任何删除通知（afterCommit 未执行）
    And <目标> 保持删除前状态（未被软删）

    Examples:
      | 删除动作      | 目标 |
      | 删除文件 F1   | F1   |
      | 删除数据集 DS1 | DS1  |

  # ============================================================
  # 四、发送失败兜底（吞掉，不影响已提交的删除）
  # ============================================================

  Scenario Outline: afterCommit 投递抛异常时删除仍成功且异常不外抛
    Given DS1 下存在文件 F1
    And 投递删除通知时底层发送器抛出异常
    When alice 执行 <删除动作>
    Then 删除接口仍返回成功（与正常删除一致）
    And <目标> 已被软删（删除已提交、不受发送失败影响）
    And 发送异常被内部捕获并告警/留痕，不向调用方抛出

    Examples:
      | 删除动作      | 目标 |
      | 删除文件 F1   | F1   |
      | 删除数据集 DS1 | DS1  |

  # ============================================================
  # 五、发送前完整性校验（缺字段不投递）
  # ============================================================

  Scenario Outline: 删除通知字段不完整时拒绝投递
    Given 一条待投递的删除通知 <缺陷>
    When 触发发送前完整性校验
    Then 该消息被判为不完整并拒绝投递
    And 不向 "tolink.rag.document_delete" 投递该消息

    Examples:
      | 缺陷                                  |
      | delete_type 缺失或非法                |
      | dataset_id 缺失                       |
      | user_id 缺失                          |
      | delete_type=file 但 original_file_id 缺失 |

  # ============================================================
  # 六、边界
  # ============================================================

  Scenario Outline: 删除目标不存在或无权访问时删除未发生且不投递通知
    Given 删除目标为 <目标> 且 <情形>
    When alice 调用对应删除接口
    Then 接口返回 404
    And 不发生任何软删
    And 不向 "tolink.rag.document_delete" 投递删除通知

    Examples:
      | 目标   | 情形           |
      | 数据集 | 数据集不存在   |
      | 数据集 | 数据集属于他人 |
      | 文件   | 文件不存在     |
      | 文件   | 文件属于他人   |

  Scenario: 删除不含任何文件的数据集仍投递一条 dataset 范围通知
    Given DS1 下当前没有任何文件
    When alice 删除数据集 DS1 且删除事务成功提交
    Then 仍投递一条 delete_type == "dataset" 且 dataset_id == DS1 的删除通知
    And 该消息不含 original_file_id 字段
