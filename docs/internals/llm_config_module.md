# LLM 配置模块

本文档描述 Java 管理端统一 LLM 配置、默认选择、数据集绑定和跨服务缓存失效。

## 模块边界

| 职责 | 入口 |
| --- | --- |
| 统一配置管理 | `LLMModelConfigServiceImpl` |
| 默认选择解析 | `LLMCapabilityDefaultServiceImpl` |
| 精确配置校验 | `LLMModelConfigValidatorImpl` |
| 数据集五类绑定校验 | `DatasetModelBindingValidator` |
| 厂商/模型目录 | `SystemProviderServiceImpl`、`ProviderModelServiceImpl` |
| 用户 HTTP | `ConfigController` |
| 管理端 HTTP | `AdminLLMConfigController` |

核心表：

| 表 | 作用 |
| --- | --- |
| `llm_system_provider` | 厂商目录与表单模板，不是运行配置 |
| `llm_provider_model` | 正式模型能力目录，可复制为运行快照 |
| `llm_model_config` | SYSTEM/USER 共用的可执行配置；`id` 是全局 `configId` |
| `llm_capability_default` | SYSTEM 平台兜底与 USER 默认覆盖 |
| `dataset_parse_config` | 数据集五类稳定配置绑定及解析/召回参数 |

旧 `llm_system_preset`、`llm_user_config` 已删除。`scope` 只控制权限和展示，三端不再使用 `source + id` 联合定位。

## 用户配置与默认选择

用户配置行为保持“添加模型、选择模型”两步，但数据职责分离：

1. 用户从厂商/模型目录选择模型，提交 API Key，Java 写入或更新一条 `scope=USER` 的 `llm_model_config` 运行快照。
2. 列表接口返回用户自己的配置和当前 SYSTEM 默认配置，身份始终为 `configId`。
3. 用户通过 `PUT /api/v1/llm/defaults/{capability}` 设置默认，或 DELETE 清除覆盖。
4. 有效默认解析固定为 USER 默认 → SYSTEM 默认；都不存在时返回 `LLM_DEFAULT_NOT_CONFIGURED(10024)`。

默认关系不混入配置行。停用/删除 USER 配置时同步清除指向它的 USER 默认；不能因此修改平台默认或已经固化的数据集绑定。

## 管理端平台配置

`POST /api/v1/admin/llm/configs` 与 `PUT /api/v1/admin/llm/configs/{configId}` 接收单个原子保存请求：

- `sourceProviderModelId`：从正式目录复制运行快照；或
- `catalogMutation`：在同一事务创建/更新目录项，再生成运行快照。

两种事实来源二选一。API Key 加密存储且响应脱敏。`setAsDefault=true` 时配置保存与 SYSTEM 默认切换处于同一事务；`clearDefault=true` 时只清除仍指向当前配置的 SYSTEM 默认关系；两者均为 `false` 时保持默认关系不变。默认关系写入或清除失败时整单回滚。

平台配置可以在同一 capability 内刷新模型、协议、入口和密钥并递增 `snapshot_version`，但不能原地改变 capability；能力变化必须新建配置，避免旧默认关系或数据集绑定指向错误能力。

停用/删除当前 SYSTEM 默认必须同时提供同能力替代项；替代关系和配置状态原子提交。紧急停用允许保留引用，但后续预检与执行必须因 `is_active=false` 拒绝该配置。

## 精确执行校验

Chat、数据集创建、解析配置保存、召回 session 签发和 Python 执行都按 `configId` 校验，优先级不可变化：

1. 不存在：`10020`；
2. 已停用：`10021`；
3. USER 配置越权：`10022`；
4. 能力不匹配：`10023`。

SYSTEM 配置对所有用户可执行；USER 配置只允许所有者执行。Chat 必须精确匹配 `CHAT`，不得以默认模型或其它能力隐式替代。

## 数据集绑定

`dataset_parse_config` 直接保存五个全局 ID：

- 稀疏向量 `SPARSE_EMBEDDING` 与稠密向量 `EMBEDDING`：创建数据集时必需，固化后不可修改；
- 增强对话 `CHAT`：表格或标题层级增强开启时必需；
- 增强视觉 `VISION`：图片增强开启时必需；
- 重排 `RERANK`：`enable_rerank=true` 时必需。

后三项可以替换或清空。只要提交了 ID，即使功能暂时关闭也做存在、active、归属和能力预检。召回 session 签发与 Python 运行前会再次检查已存绑定，防止停用或删除后的旧 ID 继续执行。

该表的高频执行读取使用 Java/Python 共用的原始快照缓存：

- data：`cache:dataset:parse-config:{dataset-config:<datasetId>}`；
- fence：`cache:fence:dataset:parse-config:{dataset-config:<datasetId>}`；
- lock：`cache:lock:dataset:parse-config:{dataset-config:<datasetId>}`。

缓存 value 只保存表中五个 ID、四类原始 JSON、user/dataset 与 active 事实。Java 的接口展示默认和 Python 的运行期 Settings 都在命中后于内存中合并，不进入共享 value；因此同一缓存既不会锁死 Python 环境默认，也不会把 Java 展示规则误当成用户显式配置。

## 缓存一致性

Java 的配置管理与校验读取 MySQL 权威数据，不读取 Python runtime cache。Python 执行端按 `configId` 缓存解密后的运行快照，使用同槽 data/fence/lock 三键：

- data：`cache:llm:runtime-config:{llm-runtime:<configId>}`；
- fence：`cache:fence:llm:runtime-config:{llm-runtime:<configId>}`；
- lock：`cache:lock:llm:runtime-config:{llm-runtime:<configId>}`。

Java 写事务提交后执行 `fence++ + DEL data` 首删；Canal 捕获 `llm_model_config` INSERT/UPDATE/DELETE，经 `tolink.cache.evict` 发送 `llm_runtime_config:<configId>` 做补偿失效。Python 回填前后比较 fence，变更期间的慢查询不得把旧值写回。缓存与 CDC readiness 默认关闭，发布顺序见 `docs/internals/cache_module.md`。

## 修改注意事项

- 新增 capability 时同步 Java/Python 能力校验、数据集绑定、默认选择和用量上报。
- 不得在日志、DTO、Redis key 或异常中输出 API Key 明文或密文。
- 生产表结构以 Python Alembic 为权威；Java `init.sql` 与 H2 schema 只做镜像。
- API、MySQL、MQ 或 Redis 契约变更必须同步对应 `docs/` 文档并运行文档同步检查。
