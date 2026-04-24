# MQ / Kafka 组件说明

## 1. 组件定位

MQ 组件是项目的异步消息 framework，负责提供：

- 统一消息抽象 `AbstractMQ`
- 统一发送接口 `MQSend`
- Kafka / RabbitMQ 适配
- 消息拓扑扫描与自动装配
- 消费者接入约定

它解决的是“如何接入和发送/接收 MQ 消息”，不是“某个具体业务消息该怎么设计补偿策略”。

## 2. 代码归属

- 模块：`link-components/toLink-components-mq`
- 主要包：`com.qingluo.link.components.mq`

## 3. 核心代码结构

| 代码 | 作用 |
| --- | --- |
| `AbstractMQ` | 业务消息模型抽象 |
| `MQSend` | 统一发送接口 |
| `MQMsgReceiver` | 统一消费者接口 |
| `MQProperties` | MQ 组件配置 |
| `MQVenderChoose` | MQ 厂商选择常量 |
| `KafkaMQSend` | Kafka 发送实现 |
| `RabbitMQSend` | RabbitMQ 发送实现 |
| `KafkaMQTopologyScanner` | Kafka 消息模型扫描与 topic 注册 |
| `RabbitMQTopologyScanner` | RabbitMQ 拓扑扫描与注册 |

## 4. 这个组件能做什么

### 4.1 `AbstractMQ`

定义一个业务消息最小需要提供的内容：

- `getMQName()`
- `getMQType()`
- `getMessage()`

也就是说，业务方只要定义好“消息名、发送语义、序列化消息体”，就能接入统一 MQ 发送流程。

### 4.2 `MQSend`

提供统一发送能力：

- 普通发送
- 带 delay 参数的发送接口

注意：

- Kafka 当前实现不支持真正的延迟消息

### 4.3 自动装配与拓扑扫描

组件会根据配置：

- 选择 Kafka 或 RabbitMQ
- 扫描 `AbstractMQ` 实现
- 进行必要的 topic / queue / exchange 注册

## 5. AI 什么时候应该用这个组件

当你要做的事情符合下面任一情况时，应优先考虑这个组件：

- 异步任务下发
- 外部系统结果回传
- 需要解耦业务链路
- 需要新增 topic / queue / consumer

如果需求是：

- 单体内普通方法调用
- 必须同步返回结果
- 只是新增数据库状态流转

那不应该先引入 MQ。

## 6. AI 接入时怎么操作

### 6.1 只是新增一个业务消息

推荐做法：

1. 在业务模块里新建一个 `AbstractMQ` 实现
2. 定义：
  - `MQ_NAME`
  - `MQSendType`
  - `getMessage()`
3. 在业务 Service 中注入 `MQSend`
4. 在合适时机调用 `mqSend.send(...)`

消息模型通常放在：

- 业务模块自己的 `mq` 包下
- 或业务实现类内部私有消息模型

不要放在 framework 模块，除非它是通用消息抽象本身。

### 6.2 想新增一个消费者

推荐做法：

1. 在业务模块中新增消费者接入类
2. 绑定对应 topic / group / queue
3. 消费后委托给业务 service 处理

消费者代码通常放在：

- `link-service/.../mq/`
- 或 `link-service/.../mq/kafka/`

### 6.3 想扩展 MQ framework

只有在下面情况才应该改 framework 模块：

- 要新增 MQ 厂商
- 要增强消息模型扫描机制
- 要增强统一发送抽象

这类代码应放在：

- `link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/`

## 7. AI 需要写哪些代码

### 场景一：业务接入 MQ

通常需要写：

- 一个 `AbstractMQ` 实现
- 业务 service 中的发送调用
- 需要的话，再写一个消费者接入类

通常不需要改：

- `MQSend`
- `MQProperties`
- vendor 自动装配

### 场景二：扩展 MQ framework

通常需要写：

- 新厂商发送实现
- 自动装配类
- 拓扑扫描或注册扩展

## 8. 关键配置

- 配置前缀：`qingluopay.mq`
- 厂商选择键：
  - `qingluopay.mq.vender`
  - `qingluopay.mq.vendor`
- 关键配置包括：
  - `scan-base-packages`
  - `kafka-auto-create-topics`
  - `kafka-topic-partitions`
  - `kafka-topic-replicas`
  - `delayed-exchange-name`
  - `fanout-exchange-name-prefix`

## 9. 不该怎么用

- 不要为了“可能以后异步”就提前引入 MQ
- 不要让业务代码直接依赖 Kafka/RabbitMQ 原生 API
- 不要把具体业务补偿策略硬编码到 framework 组件里
- 不要定义没有业务主键或 taskId 的消息体

## 10. 读取顺序建议

当 AI 要使用 MQ 组件时，推荐顺序：

1. 先读本文件
2. 再读 `docs/architecture/middleware_contract.md` 中的 MQ 约定
3. 再看当前业务模块是否已有消息模型和消费者写法
4. 最后在 `technical_design.md` 中写本次业务接入方式
