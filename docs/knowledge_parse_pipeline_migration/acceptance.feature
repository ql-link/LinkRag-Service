Feature: Java 解析链路按 Python 数据契约协作

  Scenario: 上传成功后建立解析聚合记录
    Given 用户向其数据集上传一个有效知识文件
    When 原文件保存成功
    Then 系统创建唯一的 document_parse_file 记录
    And 原文件记录不承载解析状态或解析产物字段

  Scenario: Java 发送扁平解析任务消息
    Given 已上传成功且没有进行中任务的原文件
    When 用户提交解析
    Then Java 更新 document_parse_file.latest_parse_task_id
    And Java 向 tolink.rag.parse_task 发送包含 document_parse_file_id 的扁平 JSON 消息

  Scenario: Python 尚未创建日志时阻止重复投递
    Given document_parse_file 已有 latest_parse_task_id
    And document_parsed_log 尚无对应 task_id
    When 用户再次提交同一文件解析
    Then 系统拒绝重复提交

  Scenario: Java 消费终态结果但不写终态
    Given Python 已写入 document_parsed_log 的解析终态
    When Java 收到 tolink.rag.parse_result 的扁平消息
    Then Java 使用 document_parsed_log_id 与 task_id 校验任务和归属
    And Java 发布终态 SSE 事件
    And Java 不更新解析结果持久化字段

  Scenario: 前端查询最新解析结果
    Given 一个文件存在多次解析日志
    When 前端查询该文件解析结果
    Then 系统按 document_parse_file.latest_parse_task_id 返回当前任务状态和解析文件名
