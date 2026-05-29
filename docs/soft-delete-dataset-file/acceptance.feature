# 数据集/文件隐性删除（软删保留原文件）验收契约
# 前提：
#   - 隐性删除：删除不物理删 OSS 原文件对象、不物理删原文件 DB 行；只标记软删隐藏。
#   - 全库仅 dataset / document_original_file 两表软删（接入 @TableLogic）；其余表删除一律物理删。
#   - 同名重建/重传靠「判别列纳入唯一键」：活行取统一值、软删时取该行自身唯一值，使死行退出活名额；
#     验收只断言可观测行为（重建/重传成功、不撞唯一约束、多轮不冲突、死行各自留存），不断言列名/取值实现。
#   - 会话/消息一律物理删：chat_conversation 移除 is_deleted/@TableLogic；chat_message 本就无软删字段。
#   - 解析域（document_parse_file / document_parsed_log + Python 侧 OSS 产物）交 Python 清理；
#     Java 删除路径本次起不再触碰 parse 两表（移除原 deleteParseRecords）。
#   - 通知 Python 删产物仅占位：删除事务提交后（afterCommit）触发预留发送点，本次不落 producer/topic/消息体。
#   - 删除接口对外形态（HTTP 返回/入参/URL）不变；前端对软删无感。
#   - 唯一约束：uk_dataset_user_name (user_id, name)、uk_dataset_user_name_suffix (dataset_id, user_id, original_filename, file_suffix)。
#   - 验收由 JUnit/Mockito/MockMvc/SpringBootTest 承接，不引入 Cucumber。

Feature: 数据集/文件隐性删除（软删保留原文件）
  作为已登录用户
  我希望删除数据集或文件时原文件被保留（软删隐藏、不物理删 OSS）
  以便可追溯/恢复，并能在删除后重新创建或上传同名而不被唯一约束卡住

  Background:
    Given 用户 alice 已登录
    And alice 拥有数据集 DS1

  # ============================================================
  # 一、隐性删除主流程（保留原文件，不物理删 OSS）
  # ============================================================

  Scenario: 删除单个文件改为软删并保留原文件
    Given DS1 下存在上传成功的文件 F1 且其原文件对象位于 OSS（对象位置 K1）
    When alice 删除文件 F1
    Then 删除接口返回成功（与改造前的成功语义一致）
    And F1 不再出现在文件列表与详情中
    And 删除过程中未对原文件对象执行 OSS 物理删除
    And OSS 中对象 K1 仍然存在
    And F1 的原文件 DB 行物理仍在且被标记为已软删

  Scenario: 删除数据集级联软删名下文件并保留原文件
    Given DS1 下存在上传成功的文件 F1、F2（对象位置 K1、K2）
    When alice 删除数据集 DS1
    Then 删除接口返回成功
    And DS1 不再出现在数据集列表与详情中
    And F1、F2 不再出现在文件列表中
    And 删除过程中未对 F1、F2 的原文件对象执行 OSS 物理删除
    And OSS 中对象 K1、K2 仍然存在
    And DS1、F1、F2 的 DB 行物理仍在且均被标记为已软删

  # ============================================================
  # 二、删后同名重建/重传（判别列让其走通，可无限轮）
  # ============================================================

  Scenario: 删除文件后在同一活数据集重传同名成功
    Given DS1 下文件 "a.pdf" 已被软删
    When alice 向 DS1 重新上传同名文件 "a.pdf"
    Then 重传成功并创建一条新的活文件记录
    And 不因唯一约束 uk_dataset_user_name_suffix 报重名或冲突
    And 新活记录与被软删的旧记录是不同的两行（旧死行仍物理保留）

  Scenario: 同名文件反复删除与重传多轮均不冲突
    Given alice 对 DS1 中文件 "a.pdf" 已完成 3 轮「上传成功 → 删除」
    And 因此 DS1 中存在 3 条已软删的 "a.pdf" 死行
    When alice 第 4 次上传同名 "a.pdf"
    Then 第 4 次上传成功并创建新的活记录
    And 包含第 2 次及以后的每一次删除在内，全程均未触发 uk_dataset_user_name_suffix 唯一约束冲突
    And DS1 中此刻有且仅有 1 条活的 "a.pdf" 记录
    And 3 条历史死行各自独立保留、互不冲突

  Scenario: 删除数据集后重建同名数据集成功
    Given alice 的数据集 DS1（name="财务"）已被软删
    When alice 重新创建同名数据集 name="财务"
    Then 创建成功并得到一个新的活数据集（新数据集标识）
    And 不因唯一约束 uk_dataset_user_name 报"已存在同名数据集"
    And 被软删的旧数据集死行仍物理保留

  Scenario: 软删死行连同其 OSS 对象保留且不产生孤儿
    Given DS1 中文件 "a.pdf"（对象位置 K1）已被软删
    When alice 向 DS1 重传同名 "a.pdf" 并上传成功（对象位置 K2）
    Then 被软删的旧行仍保留其对象位置 K1
    And OSS 中对象 K1 与 K2 都存在（K1 未被物理删除）
    And 新活行的对象位置 K2 与 K1 不同
    And 不存在无任何 DB 行引用的 OSS 孤儿对象

  # ============================================================
  # 三、与既有同名复用逻辑共存（上传路径不被软删破坏）
  # ============================================================

  Scenario Outline: 同名命中活记录(非 failed)仍同步 400 拦截，死行不改变结论
    Given DS1 中存在活的 "a.pdf" 记录且其 uploadStatus == <状态>
    And DS1 中另可能存在已软删的 "a.pdf" 死行
    When alice 再次上传同名 "a.pdf"
    Then 接口同步返回 400（已存在同名文件）
    And 不创建新记录
    And 不复用任何死行

    Examples:
      | 状态      |
      | uploading |
      | success   |

  Scenario: 同名命中活 failed 记录仍复用该行，软删死行不参与
    Given DS1 中存在活的 "a.pdf" 记录且其 uploadStatus == failed
    And DS1 中另存在若干已软删的 "a.pdf" 死行
    When alice 再次上传同名 "a.pdf"
    Then 复用该活 failed 行并重置为 uploading（不新增记录）
    And 软删死行不被复用、保持原状
    And 不触发唯一约束冲突

  # ============================================================
  # 四、会话/消息物理删 + 去除 chat_conversation 软删
  # ============================================================

  Scenario: 删除数据集级联物理删除名下会话与消息
    Given DS1 下存在会话 C1 且 C1 含消息 M1、M2
    When alice 删除数据集 DS1
    Then C1 的 DB 行被物理删除（表中不再存在该行）
    And M1、M2 的 DB 行被物理删除
    And 不存在 C1 的软删隐藏行（会话不再有软删保留语义）

  Scenario: 单独删除会话改为物理删除
    Given alice 拥有会话 C1 且 C1 含若干消息
    When alice 删除会话 C1
    Then C1 的 DB 行被物理删除（非软删隐藏）
    And C1 名下消息被物理删除

  Scenario: 去除 chat_conversation 软删后会话列表行为不变
    Given alice 拥有若干未删除会话（含置顶与非置顶）
    When alice 查询会话列表
    Then 返回其全部未删除会话且按置顶、更新时间倒序排列（与去除软删字段前一致）
    And 已删除会话不出现在列表中（因已被物理删除、不在表中）

  # ============================================================
  # 五、解析域交 Python（Java 删除路径不再触碰 parse 两表）
  # ============================================================

  Scenario: 删除文件后 Java 不删除其解析派生行
    Given 文件 F1 已解析且存在其 document_parse_file 行与 document_parsed_log 行
    When alice 删除文件 F1
    Then F1 被软删隐藏
    And F1 对应的 document_parse_file 行物理仍在
    And F1 对应的 document_parsed_log 行物理仍在
    And 删除过程未执行原 deleteParseRecords 的解析行清理

  Scenario: 删除数据集后 Java 不删除名下文件的解析派生行
    Given DS1 下文件 F1 已解析且存在其 document_parse_file 行与 document_parsed_log 行
    When alice 删除数据集 DS1
    Then F1 被软删隐藏
    And F1 对应的 document_parse_file 与 document_parsed_log 行物理仍在（保留交 Python 清理）

  # ============================================================
  # 六、通知 Python 删产物（占位：afterCommit 时机与载荷，不实际投递）
  # ============================================================

  Scenario: 删除文件在事务提交后触发占位删除通知发送点
    Given DS1 下存在文件 F1
    When alice 删除文件 F1 且删除事务成功提交
    Then 在事务提交后（afterCommit）触发一次"通知 Python 删除"的预留发送点
    And 该发送点的载荷包含被软删的 original_file_id（此处为 F1）
    And 本次不实际投递 MQ 消息（producer 未落地，仅占位/留痕）

  Scenario: 删除数据集的占位通知载荷包含级联出的整批文件标识
    Given DS1 下存在文件 F1、F2
    When alice 删除数据集 DS1 且删除事务成功提交
    Then 在事务提交后触发一次"通知 Python 删除"的预留发送点
    And 该发送点的载荷包含 F1、F2 的 original_file_id 集合

  Scenario: 删除事务回滚则不触发删除通知且不产生软删
    Given alice 删除文件 F1 的过程在事务内因异常回滚
    When 事务结束
    Then 不触发"通知 Python 删除"的发送点（afterCommit 不执行）
    And F1 保持删除前状态（未被软删）

  # ============================================================
  # 七、不变量与边界
  # ============================================================

  Scenario: 软删文件经内部原文件下载接口返回 404
    Given 文件 F1 已被软删
    When 通过内部原文件下载接口请求 F1
    Then 返回 404（文件不存在）

  Scenario Outline: 删除目标不存在或无权访问仍返回 404 且不发生任何删除
    Given 删除目标为 <目标> 且 <情形>
    When alice 调用对应删除接口
    Then 接口返回 404
    And 不发生任何软删或物理删除

    Examples:
      | 目标       | 情形           |
      | 数据集     | 数据集不存在   |
      | 数据集     | 数据集属于他人 |
      | 文件       | 文件不存在     |
      | 文件       | 文件属于他人   |

  Scenario: 删除接口对外形态不变（前端无感）
    Given DS1 下存在文件 F1
    When alice 删除文件 F1
    Then 删除接口的 HTTP 返回形态与改造前一致（成功语义、入参、URL 不变）
    And 前端无需感知软删（F1 仅从列表中消失）

  Scenario: 删除链路不物理删 OSS（贯穿不变量）
    Given 任意一次数据集删除或文件删除
    When 删除完成
    Then 全程未对原文件对象调用 OSS 物理删除
    And 相关原文件对象在 OSS 中均被保留

  Scenario: 软删仅限原文件与数据集两表（贯穿不变量）
    Given 一次数据集删除涉及原文件、数据集、会话、消息、解析派生行
    When 删除完成
    Then 仅 dataset 与 document_original_file 表的相关行为软删（标记隐藏、物理行保留）
    And chat_conversation 与 chat_message 表的相关行为物理删除
    And document_parse_file 与 document_parsed_log 表的相关行不被 Java 删除（保留交 Python）
