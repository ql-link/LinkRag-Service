# LinkRag Java 管理端 · 项目介绍 PPT 提纲

> 听众：团队新成员（学弟）
> 目标：理解项目业务逻辑 + 知道如何参与开发
> 时长：~40 分钟
> 重点：文件上传解析链路要讲透

---

## 第一部分：项目是什么（5 分钟）

### P1 封面
- LinkRag · Java 管理端技术全景

### P2 产品定位（2 分钟）
- **LinkRag 是什么**：面向 C 端的通用 RAG 产品
- **用户故事**：用户上传文档 → 系统解析成知识 → 用户基于知识对话
- **一句话**：让普通用户也能用自己的文档跟 AI 对话
- 配图：用户视角的产品流程简图（上传 → 解析 → 对话）

### P3 双服务架构（2 分钟）
- **Java 管理端**（本项目）：管用户、管文件、管配置、管状态、推事件
- **Python RAG 执行端**：干解析、做向量、跑 RAG、调 LLM
- **协作方式**：MySQL 共享 + Kafka MQ + OSS 文件 + 内部 HTTP
- 配图：两个大方块 + 中间的 4 条协作通道

### P4 用户能做什么（1 分钟）
- 注册登录 → 配置 LLM API Key → 创建数据集 → 上传文件 → 等解析 → 开始对话
- 这一页就是产品功能的「用户旅程」，让学弟先有全局画面

---

## 第二部分：代码怎么组织的（5 分钟）

### P5 Maven 模块结构（2 分钟）
- 6 个模块的职责一句话说清：
  - **link-api**：Controller + 启动类，所有 HTTP 入口
  - **link-service**：业务逻辑，最厚的一层
  - **link-mapper**：MyBatis-Plus Mapper，纯数据访问
  - **link-model**：Entity / DTO / Enum / 统一响应
  - **link-core**：异常体系 + 认证上下文 + 加密工具
  - **link-components**：Redis / MQ / OSS 三大横向组件
- 配图：模块依赖方向图（api → service → mapper/components/core/model）

### P6 技术栈速览（1 分钟）
- Java 17 / Spring Boot 2.5.3 / MyBatis-Plus / Sa-Token / MySQL 8 / Redis / Kafka / MinIO
- 测试：JUnit 5 + Mockito + SpringBootTest + EmbeddedKafka
- 不用展开讲，让学弟知道用了什么就行

### P7 数据库核心表（2 分钟）
- 按业务域分组列出 10 张表：
  - **用户域**：sys_user
  - **LLM 域**：llm_system_provider / llm_user_config / llm_usage_log
  - **对话域**：chat_conversation / chat_message
  - **数据集域**：datasets
  - **文件解析域（重点）**：document_original_file / document_parse_file / document_parsed_log
  - **配置域**：上传配置运行时位于 Redis
- 配图：ER 关系简图，重点标注解析三表之间的关系

---

## 第三部分：三大横向组件的代码架构（7 分钟）

> 这部分让学弟理解 link-components 里 MQ / Redis / OSS 是怎么抽象的，以后改组件知道从哪下手。

### P8 组件层总览（1 分钟）
- link-components 下有 3 个子模块：
  - `toLink-components-mq` — 消息队列抽象
  - `toLink-components-redis` — 缓存读保护 + 写一致性
  - `toLink-components-oss` — 对象存储抽象
- **设计原则**：业务 Service 只依赖接口/抽象类，不直接依赖 Kafka/MinIO/Redis 具体 API

**📊 配图：三层架构总览图（SVG）**
```
┌─────────────────────────────────────────────────────────────────┐
│  link-service (业务层)                                           │
│  ┌──────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │ MQSend.send()│  │ CacheReadProtect │  │ IOssService       │  │
│  │              │  │ .getOrLoad()     │  │ .upload()         │  │
│  │              │  │ CacheConsistency │  │ .deleteFile()     │  │
│  │              │  │ .evict()         │  │                   │  │
│  └──────┬───────┘  └────────┬─────────┘  └────────┬──────────┘  │
├─────────┼───────────────────┼──────────────────────┼────────────┤
│  link-components (组件层)    │                      │            │
│         ▼                   ▼                      ▼            │
│  ┌─────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │ KafkaMQSend │  │ RedisTemplate    │  │ LocalFileService  │  │
│  │ (Kafka实现) │  │ + Lock + Marker  │  │ MinioFileService  │  │
│  └──────┬──────┘  └────────┬─────────┘  └────────┬──────────┘  │
├─────────┼───────────────────┼──────────────────────┼────────────┤
│  外部中间件                  │                      │            │
│         ▼                   ▼                      ▼            │
│  ┌─────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │ Kafka Broker│  │ Redis Server     │  │ MinIO / 本地文件   │  │
│  └─────────────┘  └──────────────────┘  └───────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

### P9 MQ 组件架构（2.5 分钟）

**📊 配图①：类继承/实现关系图（UML 类图风格，SVG 绘制）**
```
        «interface»                    «interface»
       ┌───────────────┐             ┌──────────┐
       │   AbstractMQ  │             │  MQSend  │
       ├───────────────┤             ├──────────┤
       │+getMQName()   │             │+send(mq) │
       │+getMQType()   │             └────┬─────┘
       │+getMessage()  │                  │ implements
       └───────┬───────┘                  ▼
               │ implements        ┌──────────────┐
      ┌────────┼────────┐         │ KafkaMQSend  │
      ▼        ▼        ▼         ├──────────────┤
┌──────────┐┌──────────┐┌──────────┐│-kafkaTemplate│
│ParseTask ││ParseResult││CacheComp ││+send(mq)    │
│MQ        ││MQ         ││MQ        │└──────────────┘
│topic:    ││topic:     ││topic:    │
│parse_task││parse_result││cache.evict│
└──────────┘└──────────┘└──────────┘

        «interface»
       ┌──────────────┐
       │MQMsgReceiver │
       ├──────────────┤
       │+receive(msg) │
       └──────┬───────┘
              │ implements
     ┌────────┼──────────────┐
     ▼                       ▼
┌────────────────┐  ┌──────────────────────┐
│ParseResult     │  │CacheCompensation     │
│KafkaReceiver   │  │KafkaReceiver         │
│(专用容器工厂)   │  │(默认容器工厂)         │
└────────────────┘  └──────────────────────┘
```

**📊 配图②：MQ 消息从定义到投递的流程图（SVG 绘制）**
```
开发者写一个类 implements AbstractMQ
         │
         ▼
KafkaMQTopologyScanner 启动时扫描 classpath
         │ 发现 getMQName() = "tolink.rag.parse_task"
         ▼
KafkaMQAutoConfiguration → TopicBuilder.name(...).build()
         │ 自动向 Kafka 注册 Topic
         ▼
业务 Service 调用 MQSend.send(new KnowledgeParseTaskMQ(payload))
         │
         ▼
KafkaMQSend → kafkaTemplate.send(topic, jsonMessage)
         │
         ▼
    Kafka Broker → Consumer 消费
```

**讲述要点**：
- 新增消息只需：① 写一个类 implements AbstractMQ ② Topic 自动创建 ③ 写 Receiver
- Kafka/RabbitMQ 切换只改配置 `tolink.mq.vendor`，业务代码不动
- parse_result 用专用容器工厂（隔离错误处理），cache.evict 用默认工厂

**📋 各类职责速查表（PPT 上用表格或卡片展示）**

| 类名 | 所在模块 | 一句话职责 |
|------|----------|-----------|
| `AbstractMQ` | components/mq | 消息模型接口：定义 topic 名 + 序列化方式 |
| `MQSend` | components/mq | 发送接口：业务只调 `send(mq)`，不关心底层是 Kafka 还是 RabbitMQ |
| `MQMsgReceiver` | components/mq | 消费接口：消费者实现 `receive(msg)` |
| `KafkaMQSend` | components/mq/vender/kafka | Kafka 发送实现：把 AbstractMQ 翻译成 `kafkaTemplate.send(topic, json)` |
| `KafkaMQAutoConfiguration` | components/mq/vender/kafka | 条件装配：`vendor=kafka` 时注入 KafkaMQSend + 扫描 Topic |
| `KafkaMQTopologyScanner` | components/mq/vender/kafka | 启动扫描：找到所有 AbstractMQ 实现类 → 自动创建对应 Kafka Topic |
| `KnowledgeParseTaskMQ` | components/mq/model | 解析任务消息模型：topic=`tolink.rag.parse_task`，Java→Python |
| `KnowledgeParseResultMQ` | components/mq/model | 解析结果消息模型：topic=`tolink.rag.parse_result`，Python→Java |
| `CacheCompensationMQ` | service/mq | 缓存补偿消息模型：topic=`tolink.cache.evict` |
| `KnowledgeParseResultKafkaReceiver` | service/mq | parse_result 消费者：专用容器工厂，失败分类+退避重试 |
| `CacheCompensationKafkaReceiver` | service/mq | cache.evict 消费者：默认容器工厂，调 evictCompensation() |
| `ParseResultKafkaConfig` | service/mq/config | 专用容器工厂配置：SeekToCurrentErrorHandler + 指数退避 |

### P10 Redis 组件架构（2.5 分钟）

**📊 配图①：Redis 组件类关系图（UML 风格，SVG 绘制）**
```
┌─────────────────────────────────────────────────────────────┐
│  link-service/cache (业务缓存封装)                            │
│  ┌──────────────────┐  ┌──────────────────────────────────┐ │
│  │ UserCacheService │  │ KnowledgeFileConfigCacheService  │ │
│  │ .getUser(id)     │  │ .getConfig()                     │ │
│  └────────┬─────────┘  └──────────────┬───────────────────┘ │
│           │ 调用                       │ 调用                │
├───────────┼────────────────────────────┼────────────────────┤
│  link-components/redis (组件层)         │                    │
│           ▼                            ▼                    │
│  ┌────────────────────────┐  ┌─────────────────────────┐   │
│  │CacheReadProtectionService│  │CacheConsistencyService │   │
│  │.getOrLoad(key,loader)  │  │.evict(target, id)      │   │
│  └────────────────────────┘  └───────────┬─────────────┘   │
│                                          │ 依赖             │
│                              ┌───────────▼─────────────┐   │
│                              │    CacheKeyRouter       │   │
│                              │ .route(target, id)      │   │
│                              │ → List<String> keys     │   │
│                              └───────────┬─────────────┘   │
│                                          │ 依赖             │
│                              ┌───────────▼─────────────┐   │
│                              │   CacheEvictTarget      │   │
│                              │ (enum)                  │   │
│                              │ USER / LLM_CONFIG /     │   │
│                              │ USER_DEFAULT_LLM /      │   │
│                              │ SYSTEM_PROVIDER         │   │
│                              └─────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**📊 配图②：getOrLoad() 读保护时序图（SVG 绘制）**
```
Service          Redis           Lock(key)        DB
  │                │                │              │
  │── ① GET key ──▶│                │              │
  │◀── ② null ─────│                │              │
  │── ③ tryLock ───────────────────▶│              │
  │◀── ④ acquired ─────────────────│              │
  │── ⑤ GET key ──▶│ (double-check) │              │
  │◀── ⑥ null ─────│                │              │
  │── ⑦ loader.get() ─────────────────────────────▶│
  │◀── ⑧ value ───────────────────────────────────│
  │── ⑨ SET key ──▶│                │              │
  │   TTL+rand(301)│                │              │
  │── ⑩ unlock ───────────────────▶│              │
  │                │                │              │
  │  (null 时写 __NULL__ TTL=60s)   │              │
  │  (输者 sleep 50ms 后重读缓存)    │              │
```

**📊 配图③：evict() 写一致性时序图（SVG 绘制）**
```
Service          DB              Redis           CDC/MQ
  │                │                │              │
  │── ① UPDATE ───▶│                │              │
  │◀── ② ok ───────│                │              │
  │── ③ DEL keys ──────────────────▶│              │
  │   (预算600ms, 每100ms重试)       │              │
  │◀── ④ ok / timeout ─────────────│              │
  │                │                │              │
  │   [如果③超时且syncDeleteRequired=true → 抛异常]  │
  │                │                │              │
  │                │── ⑤ binlog ───────────────────▶│
  │                │                │              │
  │◀── ⑥ consume tolink.cache.evict ──────────────│
  │── ⑦ evictCompensation() ───────▶│              │
  │   (alwaysThrow=true, 失败让MQ重试)              │
```

**讲述要点**：
- 新增缓存目标只需：① 加 `CacheEvictTarget` 枚举值 ② 在 `CacheKeyRouter` 加路由规则 ③ 写业务 CacheService
- 业务代码不直接拼 Redis key，统一走 CacheKeyRouter
- 读保护三件套：穿透（NULL marker）/ 击穿（per-key 锁）/ 雪崩（TTL 抖动）

**📋 各类职责速查表（PPT 上用表格或卡片展示）**

| 类名 | 所在模块 | 一句话职责 |
|------|----------|-----------|
| `CacheReadProtectionService` | components/redis | 统一读入口：穿透防护(NULL marker) + 击穿防护(per-key 锁) + 雪崩防护(TTL 抖动) |
| `CacheConsistencyService` | components/redis | 统一写入口：同步删(600ms 预算) + 补偿删(MQ 触发) |
| `CacheKeyRouter` | components/redis | Key 路由器：把 `(target, id)` 翻译成具体 Redis key 列表，如 `user:info:123` |
| `CacheEvictTarget` | components/redis | 枚举：定义所有缓存目标（USER / LLM_CONFIG / SYSTEM_PROVIDER 等） |
| `CacheConsistencyProperties` | components/redis | 配置类：syncDeleteRequired / syncDeleteMaxWaitMs / retryIntervalMs / nullCacheTtl / ttlJitter |
| `RedisConfig` | components/redis | 基础配置：RedisTemplate 序列化方式（Jackson + JavaTimeModule） |
| `UserCacheService` | service/cache | 业务封装：用户信息+角色缓存的读写，内部调 getOrLoad() + evict() |
| `KnowledgeFileConfigCacheService` | service/cache | 业务封装：知识文件运行配置缓存，Admin 直写 Redis，上传时读 Redis 兜底回落配置 |

---

### P11 OSS 组件架构（1 分钟）

**📊 配图：OSS 类继承关系 + 分层图（SVG 绘制）**
```
        «interface»
       ┌───────────────────────┐
       │      IOssService      │
       ├───────────────────────┤
       │+upload2PreviewUrl()   │
       │+downloadFile()        │
       │+deleteFile()          │
       │+getBucketName()       │
       └───────────┬───────────┘
                   │ implements
          ┌────────┼────────┐
          ▼                 ▼
┌──────────────────┐ ┌──────────────────┐
│ LocalFileService │ │ MinioFileService │
│ (开发环境)        │ │ (生产环境)        │
│ 读写本地文件系统   │ │ MinIO Java SDK   │
└──────────────────┘ └──────────────────┘

OssSavePlaceEnum: PUBLIC / PRIVATE
OssServiceTypeEnum: LOCAL / MINIO
OssAutoConfiguration: 根据 oss.service-type 注入对应实现

┌─────────────────────────────────────────────────┐
│  link-service/oss (业务层)                       │
│  ┌─────────────────────┐  ┌──────────────────┐ │
│  │OssUploadRuleRegistry│  │OssObjectKeyGenerator│ │
│  │ 注册各 bizType 规则  │  │ {bizType}/{uuid} │ │
│  └─────────────────────┘  └──────────────────┘ │
│                                                 │
│  PrivateFileResolver                            │
│  → 私有文件本地缓存 + evict                      │
│  → /api/v1/internal/files/{id}/content 用它     │
└─────────────────────────────────────────────────┘
```

**讲述要点**：
- 切换存储只改配置 `oss.service-type=local/minio`，业务代码不动
- 新增上传业务只需在 `OssUploadRuleRegistry` 注册规则
- private 文件不暴露直接 URL，通过内部接口走 `PrivateFileResolver`

**📋 各类职责速查表（PPT 上用表格或卡片展示）**

| 类名 | 所在模块 | 一句话职责 |
|------|----------|-----------|
| `IOssService` | components/oss | 存储接口：upload / download / delete / getBucketName，业务只依赖它 |
| `LocalFileService` | components/oss | 本地实现：文件写到 `oss.file-root-path` 目录，开发环境用 |
| `MinioFileService` | components/oss | MinIO 实现：通过 MinIO Java SDK 操作对象存储，生产环境用 |
| `PrivateFileResolver` | components/oss | 私有文件读取器：先查本地缓存，miss 则从 OSS 下载到本地再返回 File |
| `OssSavePlaceEnum` | components/oss | 枚举：PUBLIC（公开桶）/ PRIVATE（私有桶） |
| `OssServiceTypeEnum` | components/oss | 枚举：LOCAL / MINIO，决定注入哪个实现 |
| `OssAutoConfiguration` | components/oss | 条件装配：根据 `oss.service-type` 配置注入 LocalFileService 或 MinioFileService |
| `OssProperties` | components/oss | 配置类：fileRootPath / minio endpoint/accessKey/secretKey / bucket 名 |
| `OssObjectKeyGenerator` | service/oss | Key 生成器：`{bizType}/{uuid}.{suffix}`，保证全局唯一 |
| `OssUploadRuleRegistry` | service/oss | 规则注册中心：每个 bizType 注册允许的后缀、大小限制等 |
| `OssUploadRule` | service/oss | 单条规则：allowedSuffixes + maxSizeBytes + savePlaceEnum |

---

## 第四部分：核心业务链路 — 文件上传与解析（15 分钟，重点）

### P12 用户视角：一个文件的一生（2 分钟）
- 横向时间线：用户点上传 → 看到「解析中」 → 看到「解析成功」
- 让学弟先从用户视角理解「这个功能到底在干嘛」
- 关键点：用户等待期间前端通过 SSE 实时收到进度

### P13 上传流程详解（3 分钟）
- **入口**：`POST /api/v1/datasets/{datasetId}/files`（multipart）
- **KnowledgeFileServiceImpl.upload()** 的 8 步：
  1. `assertOwnedDataset(userId, datasetId)` — 验证数据集归属
  2. `validateFile(file)` — 从 Redis 读运行时配置，检查后缀（md/pdf/docx/txt）和大小（默认 20MB）
  3. `assertNoDuplicateOriginalFilename()` — 同数据集同用户不能重名
  4. INSERT `document_original_file`（status=uploading）
  5. `ossService.upload2PreviewUrl(PRIVATE, file, objectKey)` — 上传到 MinIO/本地
  6. UPDATE status=success，设置 `file_url`（内部拉取地址）
  7. `initializeParseFileIfAbsent()` — UPSERT `document_parse_file`
  8. `TransactionSynchronization.afterCommit()` → 事务提交后触发自动解析
- **为什么 afterCommit**：保证 DB 已落盘再发 MQ，避免消费者读到不存在的记录
- 配图：流程图

### P14 解析提交流程（2 分钟）
- **KnowledgeParseTaskServiceImpl.submit()** 的 3 步：
  1. `hasRunningTask()` — 双层检查防重复提交（指针 + COUNT）
  2. UPDATE `document_parse_file.latest_parse_task_id` = 新 UUID
  3. `MQSend.send(KnowledgeParseTaskMQ)` → topic: `tolink.rag.parse_task`
- **MQ 消息体字段**：task_id / original_file_id / document_parse_file_id / user_id / dataset_id / trigger_mode / file_type / source_bucket / source_object_key / md_bucket / md_object_key
- **关键设计**：`latest_parse_task_id` 是「当前任务指针」，后面所有过滤都靠它

### P15 Python 端做了什么（简述，2 分钟）
- 消费 `tolink.rag.parse_task`
- 通过 `GET /api/v1/internal/files/{fileId}/content` 拉取原始文件
- 解析文档 → 生成 Markdown → 写入 OSS（md_bucket/md_object_key）
- 过程中通过 `POST /api/v1/internal/parse-tasks/{taskId}/events` 推进度（processing/progress）
- 完成后：写 `document_parsed_log`（status=success/failed）→ 发 `tolink.rag.parse_result`
- **核心原则：Python 是权威记账人，Java 永远不写解析终态**

### P16 结果消费 + SSE 推送（3 分钟）
- **KnowledgeParseResultServiceImpl.handleParseResult()**：
  1. 按 `document_parsed_log_id` 读 Python 写入的日志
  2. 校验 taskId / status / originalFileId / datasetId / userId 全链路一致
  3. **当前任务过滤**：`payload.taskId == latest_parse_task_id` ? → 旧任务不推
  4. `publishResultEvent()` → SSE 推送 `FileParseEventDTO`
- **SSE 内存拓扑**：`ConcurrentHashMap<fileId, CopyOnWriteArrayList<SseEmitter>>`
  - 一个 fileId 可以有多个浏览器订阅
  - 断连自动清理（onCompletion / onTimeout / onError）
- **前端断连兜底**：调 `/parse-results` 接口按 `latest_parse_task_id` 读 DB 终态
- **文件状态机**：parse_waiting → parsing → parse_success / parse_failed
  - `frontendStatus()` 把 DB 状态翻译给前端

### P17 五 Lifeline 时序图（3 分钟）
- 一张完整时序图：前端 / Java / MySQL / Kafka / Python
- 13 步从上传到 SSE 推送，标注哪些是 Java 写、哪些是 Python 写
- 这页是前面 P9-P12 的总结图，讲的时候可以指着图回顾

---

## 第五部分：容错与可靠性设计（7 分钟）

### P18 为什么需要容错（1 分钟）
- MQ 消息可能丢 / 可能乱序 / 可能重复
- Redis 可能抖动
- 用户可能刷新页面
- 不做容错 → 用户永远卡在「解析中」

### P19 parse_result 消费容错（3 分钟）
- **专用 Kafka 容器工厂**：parse_result 和缓存补偿互不影响
- **失败分类**：
  - 不可重试（taskId/归属不匹配）→ NonRetryableParseResultException → 告警 + 跳过
  - 暧昧（log 暂不存在）→ ParseResultPendingException → 带退避重试（1s→2s→4s，最多 3 次）
  - 基础设施异常（DB 抖动）→ 同上退避重试
- **不引入 DLQ**：失败兜底用告警日志 + Micrometer 指标
- **当前任务过滤**：旧任务迟到不误推前端

### P20 卡住扫描兜底（2 分钟）
- `KnowledgeParseStuckScanner` @Scheduled 每 60s 扫一次
- 找 `created` 且超时 5 分钟的当前任务
- 重读 DB：已终态 → 补推 SSE；仍 created → 告警
- **核心思想**：MQ 通知降级为「提醒」，DB 才是权威源

### P21 故障场景四例（1 分钟）
- 用 4 个具体场景讲清楚容错怎么生效：
  1. **用户刷新页面** → SSE 断了 → 前端回查 DB → 立刻拿到当前状态
  2. **parse_result 消息丢了** → 卡住扫描 60s 后发现 → 重读 DB → 补推 SSE
  3. **旧任务结果晚到** → 当前任务过滤拦截 → 不推 SSE → 记审计日志
  4. **Redis 抖动** → 读降级走 DB / 写失败抛异常回滚 + binlog 补偿删

---

## 第六部分：其他业务域速览（4 分钟）

### P22 认证链路（1.5 分钟）
- Sa-Token 登录 → Bearer Token → AuthContext.getUserId()
- 所有 Service 都以 userId 做归属过滤（数据隔离的基础）
- BCrypt 密码 + last_login_at 追踪
- 角色：ADMIN / USER

### P23 LLM 配置与用量（1.5 分钟）
- 系统厂商表 → 用户 API Key（AES-256-GCM 加密存储）
- 默认配置管理（一个用户只有一个默认）
- 调用后写 llm_usage_log → summary / daily / logs 三个聚合接口
- 缓存：CacheEvictTarget.LLM_CONFIG / USER_DEFAULT_LLM

### P24 缓存架构速览（1 分钟）
- 读保护和写一致性的代码架构已在 P10 讲过
- 这里只补充**业务覆盖**：用户 / LLM 配置 / 知识文件运行配置
- 以及降级语义：读失败不阻断业务 / 写失败看 syncDeleteRequired 配置

---

## 第七部分：怎么参与开发（5 分钟）

### P25 本地启动指南（1.5 分钟）
- 环境：Java 17 + MySQL 8 + Redis + Kafka（或 H2 本地 profile 跳过外部依赖）
- 初始化：`docs/db/schema.sql` + `docs/db/init.sql`
- 启动：`mvn spring-boot:run -pl link-api`
- 端口 8080，Swagger/Knife4j 可用

### P26 开发工作流：Spec-as-Test（2 分钟）
- `brief.md → acceptance.feature → technical_design.md → Code + Tests`
- 阶段门禁：brief 不冻结不写 acceptance；acceptance 不冻结不写 TD
- 新需求放 `docs/<需求名>/`
- 提交前跑：`mvn test` + `python3 scripts/check_docs_sync.py --working`
- 文档同步规则：改了 Controller 要同步 api_contracts.md，改了 Entity 要同步 mysql_schema.md

### P27 代码导航地图（1.5 分钟）
- 想改接口 → 看 `link-api/controller` + `docs/reference/api_contracts.md`
- 想改业务逻辑 → 看 `link-service/impl/`
- 想改表 → 看 `docs/db/schema.sql` + `link-model/entity`
- 想改 MQ → 看 `docs/reference/mq_contracts.md` + `link-service/mq/`
- 想改缓存 → 看 `docs/architecture/cache_module.md` + `link-service/cache/`
- 想改 OSS → 看 `docs/architecture/object_storage_module.md` + `link-components/oss`

---

## 第八部分：收尾（2 分钟）

### P28 三句话总结
1. **Python 记账，Java 传话** — 职责边界清晰
2. **MQ 容错 + DB 兜底** — 消息丢了不怕
3. **Spec-as-Test 工程化** — 先写清楚再动手

### P29 Q&A / 封底

---

## 结构总结

| 部分 | 页数 | 时长 | 核心目标 |
|------|------|------|----------|
| 项目是什么 | P1-P4 | 5min | 建立全局画面 |
| 代码怎么组织 | P5-P7 | 5min | 知道代码在哪 |
| **三大组件架构** | P8-P11 | 7min | 理解 MQ/Redis/OSS 怎么抽象的 |
| **文件上传解析（重点）** | P12-P17 | 15min | 理解核心业务逻辑 |
| 容错与可靠性 | P18-P21 | 7min | 理解为什么这么设计 |
| 其他业务域 | P22-P24 | 4min | 补全认知 |
| 怎么参与开发 | P25-P27 | 5min | 能上手干活 |
| 收尾 | P28-P29 | 2min | 记住三句话 |

**总计 29 页，~50 分钟（含 Q&A 可控在 40-45 分钟）**

---

## 每页建议配图

| 页 | 图类型 | 内容 |
|----|--------|------|
| P2 | 产品流程图 | 上传 → 解析 → 对话 三步 |
| P3 | 架构图 | Java + Python 两大块 + 4 条协作通道 |
| P4 | 用户旅程图 | 6 步横向时间线 |
| P5 | 模块依赖图 | 6 个模块 + 箭头 |
| P7 | ER 图 | 解析三表关系 |
| P8 | 横向时间线 | 8 步从上传到成功 |
| P9 | 流程图 | upload() 8 步 |
| P10 | 流程图 | submit() 3 步 + MQ 消息体 |
| P11 | 简图 | Python 端 5 步 |
| P12 | 拓扑图 + 状态机 | SSE 内存结构 + 4 状态流转 |
| P13 | 时序图 | 5 lifeline 13 步 |
| P15 | 决策树 | 失败分类 3 路 |
| P16 | 流程图 | 卡住扫描逻辑 |
| P17 | 4 格卡片 | 4 个故障场景 |
| P20 | 时序图 | 缓存读写两条线 |
| P23 | 导航表 | 任务 → 文件路径 |
