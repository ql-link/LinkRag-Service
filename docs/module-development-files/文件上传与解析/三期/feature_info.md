# 功能信息卡

## 1. 基础信息

- 模块名称：文件上传与解析协同重构
- 当前期次：**三期**
- 业务域：storage
- 当前状态：待开发
- 复杂度等级：待定
- 当前分支：feature/knowledge_file_upload_and_parse（三期未开始）

## 2. 功能摘要

三期为一期的收尾和扩展阶段，目标是解决二期遗留的技术债务和新增必要的功能扩展。

## 3. 三期候选目标清单

| 序号 | 目标 | 描述 | 优先级 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 知识文件配置表迁移到 YAML | 取消 `knowledge_file_config` 表，将 `maxSizeBytes`、`allowedSuffixes` 等配置迁移到 `KnowledgeFileProperties` 和 `application.yml` | 高 | 简化架构，减少数据库表 |
| 2 | SSE 多实例跨节点进度分发 | 当前 SSE 为单机内存连接，多实例部署时 Python 回调节点与前端订阅节点不一致会导致事件不可见，需引入 Redis 或后端统一查询 | 中 | 技术债务 |
| 3 | MQ 投递失败即时失败化 | 取消二期“内部补偿重试、用户等待感知”的复杂方案；MQ 投递失败时直接将本次解析任务置为失败，提示用户重新解析 | 高 | 简化状态机 |
| 4 | 原文件软删除与解析产物删除 | 原文件记录软删除、前端不展示；解析产物对象由 Java 端删除；解析任务历史保留 | 中 | 功能补全 |
| 5 | 解析产物版本管理 | 保留多次成功解析产物，并支持用户选择当前生效版本 | 低 | 扩展能力 |
| 6 | 知识处理下游衔接 | 向量化、检索、问答消费等后续知识处理能力 | 低 | 扩展能力 |
| 7 | MQ 投递可靠性升级（Outbox） | 如后续需要更高可靠性，再评估本地消息表、事务消息或 broker ack 确认机制 | 低 | 长期增强，不作为当前优先方案 |

## 4. 三期目标 1 详细说明：知识文件配置表迁移到 YAML

### 4.1 背景

二期存在两套配置体系并行：
- **数据库表** `knowledge_file_config`：存储 `max_size_bytes`、`allowed_suffixes`，管理员可动态修改
- **YAML 配置** `KnowledgeFileProperties`：存储 `serviceToken`、`parseDispatchMaxRetryCount`、`sseTimeoutMs` 等

### 4.2 目标

取消数据库配置表，将所有知识文件相关配置统一到 YAML 文件管理。

### 4.3 改动范围

1. **删除数据库表** `knowledge_file_config`
2. **调整配置类** `KnowledgeFileProperties`，新增 `maxSizeBytes`、`allowedSuffixes` 等字段
3. **删除服务层**
   - `AdminKnowledgeFileConfigService` / `AdminKnowledgeFileConfigServiceImpl`
   - `KnowledgeFileRuntimeConfigServiceImpl`
   - `KnowledgeFileConfigNormalizer`
4. **删除 Controller 层**：管理员配置接口（如果有）
5. **删除 Mapper**：`KnowledgeFileConfigMapper`
6. **删除 Entity**：`KnowledgeFileConfig`
7. **调整调用方**：所有引用 `AdminKnowledgeFileConfigService` 和 `KnowledgeFileRuntimeConfigService` 的地方改为直接使用 `KnowledgeFileProperties`
8. **更新文档**：更新 `middleware_contract.md`、`init.sql` 等

### 4.4 风险与注意事项

- 取消动态配置后，修改配置需要重启服务
- 如果有前端管理员界面修改配置的入口，也需要一并移除
- `allowedSuffixes` 等配置如果有默认值，需要在 YAML 中显式声明

### 4.5 验收标准

- `knowledge_file_config` 表从数据库移除
- `KnowledgeFileProperties` 包含所有原配置表字段
- 上传服务仍能正确读取 `maxSizeBytes` 和 `allowedSuffixes` 进行校验
- 无代码引用已删除的服务和 Mapper

## 5. 关联文档

- `../二期/requirement.md` - 一期二期需求参考
- `../二期/technical_design.md` - 技术设计参考
- `docs/architecture/middleware_contract.md` - 中间件契约

## 6. 三期目标 3 详细说明：MQ 投递失败即时失败化

### 6.1 背景

二期设计中，Java 创建解析任务后发送 MQ；若 MQ 投递失败，任务保持 `created`，由内部补偿任务重试，前端在补偿期间继续展示“等待解析”。该方案虽然更偏可靠投递，但会引入较复杂的中间状态判断、补偿扫描和用户感知延迟。

### 6.2 目标

三期将解析任务投递失败策略调整为“同步投递失败即失败”：

- Java 创建解析任务后立即发送 MQ。
- MQ 投递成功：任务保持 `created`，等待 Python 消费后推进 `processing/success/failed`。
- MQ 投递失败：当前任务直接更新为 `failed`，并记录明确失败原因。
- 前端立即提示用户重新解析，不再等待内部补偿。
- 用户再次点击解析时，新建一条新的 `document_parse_task` 记录，不复用上一条失败任务。

### 6.3 状态与数据口径

- 投递失败任务状态：`task_status=failed`。
- 投递失败原因：`failure_reason=解析任务提交失败，请重新解析`。
- `parse_started_at` 保持 `null`。
- `parse_finished_at` 建议记录当前时间。
- `parse_duration_ms` 保持 `null`。
- `dispatch_retry_count`、`last_dispatched_at`、`last_dispatch_error` 等补偿字段需要重新评估：若不再使用补偿，可删除字段或保留 `last_dispatch_error` 仅作排障信息。

### 6.4 验收标准

- MQ 同步投递异常时，接口返回明确失败结果或可被前端识别的解析提交失败状态。
- 投递失败的解析任务在 `document_parse_task` 中保留一条 `failed` 历史记录。
- 用户重新解析会新增一条解析任务，不覆盖上一条失败任务。
- 不再存在后台补偿重投 `created` 任务的默认逻辑。
- 二期文档、`middleware_contract.md`、测试用例同步移除“内部补偿期间用户无感等待”的默认口径。

## 7. 实施顺序建议

1. 先完成目标 1（配置表迁移），因为优先级高且影响其他目标
2. 再完成目标 3（MQ 投递失败即时失败化），因为它能显著简化解析任务状态机和前端感知
3. 目标 1、目标 3 都适合单独提 PR
4. 后续目标可按优先级和依赖关系陆续开发
