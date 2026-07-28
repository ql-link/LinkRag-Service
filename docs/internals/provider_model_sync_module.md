# Provider Model Sync 模块

本文档描述外部模型目录同步、候选审核与发布到正式模型能力目录的实现。

## 模块边界

代码入口：

| 职责 | 入口 |
| --- | --- |
| 同步编排 | `ProviderModelSyncServiceImpl` |
| 外部目录客户端 | `ModelsDevCatalogClient` |
| 正式模型能力写入 | `ProviderModelServiceImpl` |
| 管理端 HTTP | `AdminController` 的 model-sync 接口 |

核心表：

| 表 | 作用 |
| --- | --- |
| `llm_provider_model_sync_job` | 每次同步任务的状态、统计和错误信息 |
| `llm_provider_model_sync_candidate` | 外部目录拉取后的待审核候选模型能力 |
| `llm_provider_model` | 发布后的正式模型能力目录 |

## 同步来源

当前同步来源为 `MODELS_DEV`，客户端读取 `https://models.dev/api.json`。

`ModelsDevCatalogClient` 会：

- 按 provider key 匹配厂商。
- 根据模型元数据、输入输出 modality、模型名推断 capability。
- 为每个候选生成模型名、能力、上下文长度、协议等事实。
- 设置外部来源标识为 `MODELS_DEV`。

外部接口超时时间为 10 秒。同步失败时不会修改正式模型目录。

## 任务流程

管理端调用 `/api/v1/admin/providers/{providerId}/model-sync`：

1. 创建 `RUNNING` 同步任务。
2. 从外部目录拉取该厂商模型。
3. 按 `(provider_id, source, model_name, capability)` upsert 候选记录。
4. 统计新增、更新、stale 数量。
5. 任务成功写 `SUCCESS`，失败写 `FAILED` 和错误信息。

候选记录的审核状态包括：

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 待审核，可发布或拒绝 |
| `PUBLISHED` | 已发布到正式模型能力目录 |
| `REJECTED` | 已拒绝，不进入正式目录 |

## 发布与审核

`POST /api/v1/admin/model-sync-candidates/{id}/publish` 将候选发布到 `llm_provider_model`：

- 发布时复用 `ProviderModelService.addModelCapability`。
- 发布成功后候选状态变为 `PUBLISHED`。
- 正式目录仍由管理端启停控制。

管理端按 `(provider_id, source, external_model_id)` 将能力候选聚合成一个模型展示项。`POST /api/v1/admin/model-sync-candidates/publish` 接收同一模型下勾选的多个候选 ID，并在同一事务内逐能力发布：

- 候选表仍按模型能力分行，不修改表结构，以保留各能力独立的协议、调用入口和审核状态。
- 批量请求只允许同一厂商、来源和外部模型，重复能力或混合模型会拒绝。
- 模型名和展示名可统一覆盖；能力、协议和调用入口取各候选自身的推断值。
- 任一能力发布失败时整个批次回滚，避免部分能力成功。

`PATCH /api/v1/admin/model-sync-candidates/{id}/review` 用于把候选置为 `PENDING` 或 `REJECTED`。

## 修改注意事项

- 候选表是外部事实缓冲区，不等于可调用模型目录。
- 只有发布后的 `llm_provider_model` 才会参与用户配置和有效配置解析。
- 新增外部来源时，应保留 source 字段区分，避免覆盖现有来源候选。
- capability 推断规则变化会影响用户可选模型能力，需同步 API 和配置文档。
