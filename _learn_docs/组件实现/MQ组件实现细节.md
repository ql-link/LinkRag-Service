# MQ 组件实现细节

本文档说明 `toLink-components-mq` 组件的构建思路、核心抽象、RabbitMQ / Kafka 适配方式，以及后续业务接入方式。

## 1. 设计目标

MQ 组件采用 QingLuoPay 风格的多厂商 MQ 架构：业务只面向统一抽象编写消息模型和发送逻辑，具体 MQ 中间件能力由组件内部的 vendor 适配层处理。

核心目标如下：

- 业务代码不直接依赖 `RabbitTemplate`、`KafkaTemplate` 等厂商 API。
- 业务消息模型驱动 MQ 资源声明，例如 RabbitMQ 的 Queue / Exchange / Binding，Kafka 的 Topic。
- 通过配置选择 MQ 厂商，例如 `qingluopay.mq.vender=rabbitMQ` 或 `qingluopay.mq.vender=kafka`。
- 后续新增业务消息时，只需要新增消息模型、发送点和业务消费者，不需要重复搭建 MQ 基础设施。

## 2. 模块位置

MQ 组件位于：

```text
link-components/toLink-components-mq
```

主要代码包：

```text
com.qingluo.link.components.mq
com.qingluo.link.components.mq.vender.rabbitmq
com.qingluo.link.components.mq.vender.kafka
```

自动装配文件：

```text
link-components/toLink-components-mq/src/main/resources/META-INF/spring.factories
```

## 3. 核心抽象

### 3.1 AbstractMQ

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/AbstractMQ.java`

`AbstractMQ` 是所有业务消息模型都要实现的统一契约。

```java
public interface AbstractMQ {
    String getMQName();
    MQSendType getMQType();
    String getMessage();
}
```

字段语义：

- `getMQName()`：MQ 资源名称。RabbitMQ 下表示队列名或广播队列名；Kafka 下表示 topic 名。
- `getMQType()`：消息类型，目前支持 `QUEUE` 和 `BROADCAST`。
- `getMessage()`：序列化后的消息体，通常建议是 JSON 字符串。

业务消息模型应该只放必要 ID 和上下文，不建议直接塞完整业务对象。这样消费者可以重新查询最新数据，降低消息体膨胀和字段兼容风险。

### 3.2 MQSend

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/MQSend.java`

`MQSend` 是业务侧唯一需要注入的发送接口。

```java
public interface MQSend {
    void send(AbstractMQ abstractMQ);
    void send(AbstractMQ abstractMQ, int delay);
}
```

业务代码发送消息时只需要：

```java
mqSend.send(MyBusinessMQ.build(businessId));
```

如果使用支持延迟的 vendor，例如当前 RabbitMQ 适配，可以调用：

```java
mqSend.send(MyBusinessMQ.build(businessId), 10);
```

这里的 `10` 表示延迟 10 秒。

### 3.3 MQMsgReceiver

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/MQMsgReceiver.java`

`MQMsgReceiver` 是框架侧接收原始消息的统一接口。

```java
public interface MQMsgReceiver {
    void receive(String msg);
}
```

后续业务接消费者时，可以让 vendor listener 接到 broker 消息后，先解析原始字符串，再转发给业务定义的 receiver。

### 3.4 MQSendType

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/MQSendType.java`

当前支持两种语义：

- `QUEUE`：点对点队列语义。
- `BROADCAST`：广播语义。

需要注意：不同 MQ 厂商对这两种语义的实现方式不同。

- RabbitMQ 中 `QUEUE` 通过 queue + delayed exchange + routing key 实现。
- RabbitMQ 中 `BROADCAST` 通过 fanout exchange 实现。
- Kafka 中没有 RabbitMQ 的 exchange / binding 概念，当前模板统一发送到 topic，广播效果由不同 consumer group 实现。

### 3.5 MQVenderChoose

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/MQVenderChoose.java`

该类保存 MQ 厂商选择常量。

```java
public static final String YML_VENDER_KEY = "qingluopay.mq.vender";
public static final String RABBIT_MQ = "rabbitMQ";
public static final String KAFKA = "kafka";
```

`vender` 是为了兼容 QingLuoPay 风格配置命名保留的拼写。

### 3.6 MQProperties

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/MQProperties.java`

配置前缀：

```yaml
qingluopay:
  mq:
```

主要配置项：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `vender` | 无 | MQ 厂商，支持 `rabbitMQ` / `kafka` |
| `vendor` | 无 | 正确拼写别名，当前主要保留扩展用 |
| `scan-base-packages` | `com.qingluo` | 扫描 `AbstractMQ` 消息模型的包路径 |
| `delayed-exchange-name` | `delayExchange` | RabbitMQ 延迟交换机名称 |
| `fanout-exchange-name-prefix` | `fanout_exchange_` | RabbitMQ 广播交换机前缀 |
| `kafka-auto-create-topics` | `true` | 是否启动时声明 Kafka topic |
| `kafka-topic-partitions` | `1` | Kafka topic 默认分区数 |
| `kafka-topic-replicas` | `1` | Kafka topic 默认副本数 |

## 4. 自动装配机制

文件：`link-components/toLink-components-mq/src/main/resources/META-INF/spring.factories`

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.qingluo.link.components.mq.vender.rabbitmq.RabbitMQAutoConfiguration,\
  com.qingluo.link.components.mq.vender.kafka.KafkaMQAutoConfiguration
```

组件被业务模块依赖后，Spring Boot 会自动加载这两个自动配置类。

真正生效的 vendor 由配置决定：

```yaml
qingluopay:
  mq:
    vender: rabbitMQ
```

或者：

```yaml
qingluopay:
  mq:
    vender: kafka
```

## 5. RabbitMQ 实现

### 5.1 自动配置

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/vender/rabbitmq/RabbitMQAutoConfiguration.java`

RabbitMQ 自动配置的启用条件：

- classpath 中存在 `RabbitTemplate`。
- 配置满足 `qingluopay.mq.vender=rabbitMQ`。

启动后注册：

- `MQSend` 的 RabbitMQ 实现：`RabbitMQSend`。
- `RabbitMQTopologyScanner`。
- RabbitMQ 拓扑声明对象：`Declarables`。

### 5.2 拓扑扫描

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/vender/rabbitmq/RabbitMQTopologyScanner.java`

扫描器会在 `scan-base-packages` 指定的包路径下查找 `AbstractMQ` 实现类。

每个消息模型需要提供无参构造器，因为扫描器会通过反射实例化模型，再读取：

- `getMQName()`
- `getMQType()`

如果模型没有无参构造器，或者无法实例化，会跳过并打印 warn 日志。

### 5.3 QUEUE 拓扑

当消息模型返回：

```java
MQSendType.QUEUE
```

RabbitMQ 会声明：

- 一个持久化 Queue，名称为 `getMQName()`。
- 一个全局延迟交换机，默认名称 `delayExchange`。
- Queue 与 `delayExchange` 的 Binding，routing key 为 `getMQName()`。

延迟交换机类型为：

```text
x-delayed-message
```

并设置参数：

```text
x-delayed-type=direct
```

### 5.4 BROADCAST 拓扑

当消息模型返回：

```java
MQSendType.BROADCAST
```

RabbitMQ 会声明：

- 一个持久化 Queue，名称为 `getMQName()`。
- 一个 FanoutExchange，名称为 `fanout_exchange_` + `getMQName()`。
- Queue 与 FanoutExchange 的 Binding。

### 5.5 发送逻辑

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/vender/rabbitmq/RabbitMQSend.java`

普通 `QUEUE` 消息发送：

```java
rabbitTemplate.convertAndSend(abstractMQ.getMQName(), abstractMQ.getMessage());
```

`BROADCAST` 消息发送：

```java
rabbitTemplate.convertAndSend(
    fanoutExchangeNamePrefix + abstractMQ.getMQName(),
    "",
    abstractMQ.getMessage()
);
```

延迟消息发送：

```java
rabbitTemplate.convertAndSend(
    delayedExchangeName,
    abstractMQ.getMQName(),
    abstractMQ.getMessage(),
    message -> {
        message.getMessageProperties().setDelay(delay * 1000);
        return message;
    }
);
```

当前 RabbitMQ 适配不支持延迟广播。如果对 `BROADCAST` 消息调用延迟发送，会抛出异常。

## 6. Kafka 实现

### 6.1 自动配置

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/vender/kafka/KafkaMQAutoConfiguration.java`

Kafka 自动配置的启用条件：

- classpath 中存在 `KafkaTemplate`。
- 配置满足 `qingluopay.mq.vender=kafka`。

启动后注册：

- `MQSend` 的 Kafka 实现：`KafkaMQSend`。
- `KafkaMQTopologyScanner`。
- `KafkaAdmin.NewTopics`，用于按消息模型声明 topic。

### 6.2 Topic 扫描与声明

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/vender/kafka/KafkaMQTopologyScanner.java`

Kafka 扫描器同样会扫描 `AbstractMQ` 实现类，并通过 `getMQName()` 获取 topic 名称。

当 `kafka-auto-create-topics=true` 时，会为每个消息模型声明一个 `NewTopic`。

默认 topic 参数：

```yaml
kafka-topic-partitions: 1
kafka-topic-replicas: 1
```

### 6.3 发送逻辑

文件：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/vender/kafka/KafkaMQSend.java`

Kafka 发送时将 `getMQName()` 作为 topic，将 `getMessage()` 作为消息体。

```java
kafkaTemplate.send(abstractMQ.getMQName(), abstractMQ.getMessage());
```

Kafka 模板当前不支持延迟消息。如果调用：

```java
mqSend.send(message, delaySeconds);
```

并且 `delaySeconds > 0`，会抛出异常。

原因是 Kafka 原生没有 RabbitMQ 延迟交换机语义。后续如果需要 Kafka 延迟能力，可以单独设计延迟 topic、DB outbox、时间轮或调度任务。

## 7. 消息流转链路

### 7.1 生产者链路

```text
业务 Service
  -> 构建 AbstractMQ 消息模型
  -> 调用 MQSend.send(...)
  -> 当前 vendor 的 MQSend 实现
  -> RabbitMQ / Kafka broker
```

### 7.2 RabbitMQ 消费者链路

后续业务接入消费者时，推荐使用如下模式：

```text
RabbitMQ @RabbitListener
  -> 接收原始 String 消息
  -> 调用 MyBusinessMQ.parseMsg(msg)
  -> 转发给 MyBusinessMQ.MQReceiver
  -> 业务 receiver 执行副作用
```

### 7.3 Kafka 消费者链路

Kafka 消费者推荐使用类似模式：

```text
Kafka @KafkaListener
  -> 接收原始 String 消息
  -> 调用 MyBusinessMQ.parseMsg(msg)
  -> 转发给 MyBusinessMQ.MQReceiver
  -> 业务 receiver 执行副作用
```

## 8. 业务消息模型模板

后续新增业务消息时，可以参考以下结构：

```java
package com.qingluo.link.service.mq;

import com.alibaba.fastjson.JSONObject;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSendType;

public class MyBusinessMQ implements AbstractMQ {

    public static final String MQ_NAME = "TOPIC_MY_BUSINESS";

    private MsgPayload msgPayload;

    public MyBusinessMQ() {
    }

    public MyBusinessMQ(MsgPayload msgPayload) {
        this.msgPayload = msgPayload;
    }

    public static MyBusinessMQ build(Long businessId) {
        return new MyBusinessMQ(new MsgPayload(businessId));
    }

    public static MsgPayload parseMsg(String msg) {
        return JSONObject.parseObject(msg, MsgPayload.class);
    }

    @Override
    public String getMQName() {
        return MQ_NAME;
    }

    @Override
    public MQSendType getMQType() {
        return MQSendType.QUEUE;
    }

    @Override
    public String getMessage() {
        return JSONObject.toJSONString(msgPayload);
    }

    public interface MQReceiver {
        void receive(MsgPayload msgPayload);
    }

    public static class MsgPayload {
        private Long businessId;

        public MsgPayload() {
        }

        public MsgPayload(Long businessId) {
            this.businessId = businessId;
        }

        public Long getBusinessId() {
            return businessId;
        }

        public void setBusinessId(Long businessId) {
            this.businessId = businessId;
        }
    }
}
```

## 9. 配置示例

### 9.1 RabbitMQ

```yaml
qingluopay:
  mq:
    vender: rabbitMQ
    scan-base-packages:
      - com.qingluo
    delayed-exchange-name: delayExchange
    fanout-exchange-name-prefix: fanout_exchange_

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### 9.2 Kafka

```yaml
qingluopay:
  mq:
    vender: kafka
    scan-base-packages:
      - com.qingluo
    kafka-auto-create-topics: true
    kafka-topic-partitions: 1
    kafka-topic-replicas: 1

spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      group-id: tolink-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

## 10. 后续接入步骤

新增一个业务 MQ 场景时，建议按下面顺序做：

1. 新建一个实现 `AbstractMQ` 的消息模型。
2. 定义稳定的 `MQ_NAME`，RabbitMQ 下作为队列名，Kafka 下作为 topic 名。
3. 定义精简的 `MsgPayload`，只放 ID 和必要上下文。
4. 实现 `build(...)`、`parseMsg(String)`、`getMQName()`、`getMQType()`、`getMessage()`。
5. 定义内部业务回调接口 `MQReceiver`。
6. 在生产者业务代码中注入 `MQSend` 并调用 `mqSend.send(...)`。
7. 为当前 vendor 增加 listener，将原始消息解析后交给业务 `MQReceiver`。
8. 业务 receiver 中要考虑幂等、失败重试和重复消费。
9. 启动服务后检查 broker 中 queue / exchange / binding 或 topic 是否符合预期。

## 11. 风险与注意事项

- `AbstractMQ` 消息模型需要无参构造器，否则拓扑扫描时无法实例化。
- `getMQName()` 不能为空，否则资源声明会被跳过或发送失败。
- `getMessage()` 不能为空，否则发送前会触发参数校验异常。
- RabbitMQ 延迟消息依赖 `x-delayed-message` 插件，目标环境需要提前安装。
- RabbitMQ 当前不支持延迟广播。
- Kafka 当前不支持延迟消息。
- Kafka 的广播语义依赖 consumer group，不等同于 RabbitMQ fanout exchange。
- MQ 消费者必须设计幂等逻辑，因为消息可能重复投递或重复消费。
- 如果生产者在数据库事务提交前发送 MQ，消费者可能读到旧数据或读不到数据。重要业务建议配合事务后发送或 outbox 模式。
- 死信队列、重试策略、手动确认、监控告警目前还没有统一封装，后续接支付、订单等关键链路时需要补齐。

## 12. 当前组件边界

当前已经完成的是 MQ 基础框架：

- 统一消息模型抽象。
- 统一发送接口。
- RabbitMQ vendor 适配。
- Kafka vendor 适配。
- 基于 Spring Boot 的自动装配。
- 基于消息模型扫描的资源声明。

当前还没有实现的是业务侧链路：

- 没有具体业务消息模型。
- 没有 producer 业务调用点。
- 没有 RabbitMQ / Kafka listener 模板落到业务模块。
- 没有业务 receiver。
- 没有统一重试、死信、幂等框架。
