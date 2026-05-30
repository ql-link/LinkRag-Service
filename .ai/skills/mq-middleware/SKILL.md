---
name: mq-middleware
description: 指导如何使用 toLink-Service 的 MQ 消息中台进行消息收发、定义新消息类型以及处理多厂商适配逻辑。
when_to_use: "当用户要求接入 Kafka/RabbitMQ、发送或订阅消息、新增 MQ 消息类型、实现消息消费者或修改现有消息模型时激活。触发示例：'发送一条 MQ 消息'、'写个消费者'、'新增消息类型'、'改 MQ 消息字段'"
---

# MQ 消息中台 Skill

## 1. 架构定位

MQ 模块位于 `link-components/toLink-components-mq`，通过接口 + AutoConfiguration 实现多厂商（Kafka/RabbitMQ）切换。

**强制同步要求**：凡涉及 MQ 模块操作（新增/修改/删除消息模型、消费者、Topic、厂商适配逻辑、配置项），完成代码修改后必须：

1. 同步更新 `docs/reference/mq_contracts.md` 中的消息清单与字段说明。
2. 同步更新本 Skill 的"当前消息清单"。
3. 若本次修改未改变上述内容，在交付说明中注明"已检查 mq-middleware Skill，无需更新"。

### 核心接口

| 接口 | 位置 | 职责 |
| --- | --- | --- |
| `AbstractMQ` | `link-components/.../mq/AbstractMQ.java` | 业务消息契约：`getMQName()`、`getMQType()`、`getMessage()` |
| `MQSend` | `link-components/.../mq/MQSend.java` | 统一发送入口：`send(AbstractMQ)`, `send(AbstractMQ, int delay)` |
| `MQMsgReceiver` | `link-components/.../mq/MQMsgReceiver.java` | 框架侧原始消息接收契约：`receive(String msg)` |
| `MQSendType` | `link-components/.../mq/constant/MQSendType.java` | 投递语义枚举：`QUEUE`（点对点）、`BROADCAST`（广播） |

### 消息模型位置约定

| 场景 | 位置 |
| --- | --- |
| Java↔Python 双端共享（组件级） | `link-components/toLink-components-mq/.../mq/model/` |
| Java 内部或服务级消息 | `link-service/src/main/java/com/qingluo/link/service/mq/` |

## 2. 当前消息清单

| 消息模型 | Topic/Queue | 位置 | 方向 | 说明 |
| --- | --- | --- | --- | --- |
| `DocumentParseTaskMQ` | `tolink.rag.parse_task` | `link-service/.../mq/` | Java→Python | 文档解析任务投递 |
| `DocumentParseResultMQ` | `tolink.rag.parse_result` | `link-components/.../model/` | Python→Java | 解析终态结果回传 |
| `CacheCompensationMQ` | `tolink.cache.evict` | `link-service/.../mq/` | 补偿生产者→Java | 缓存补偿删除 |

## 3. 发送消息

用 Spring 注入 `MQSend`，不直接实例化 Kafka / RabbitMQ vendor：

```java
@Service
@RequiredArgsConstructor
public class YourService {

    private final MQSend mqSend;

    public void doSomething() {
        DocumentParseTaskMQ message = new DocumentParseTaskMQ(payload);
        mqSend.send(message);
    }

    // 延迟消息（仅 RabbitMQ 有效）
    public void doDelayed() {
        mqSend.send(message, 30); // 30 秒后投递
    }
}
```

## 4. 新增消息模型

### 4.1 选择位置

- 若消息被 Python 端消费或由 Python 产生 → 放 `link-components/.../mq/model/`
- 若消息仅在 Java 内部流转 → 放 `link-service/.../mq/`

### 4.2 代码模板

```java
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQSendType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 简要描述消息用途及方向，例如：Java 向 Python 投递的 XXX 任务消息。
 */
public class YourEventMQ implements AbstractMQ {

    public static final String MQ_NAME = "tolink.xxx.your_event";

    private MsgPayload msgPayload;

    public YourEventMQ() { this.msgPayload = new MsgPayload(); }
    public YourEventMQ(MsgPayload msgPayload) { this.msgPayload = msgPayload; }

    /** 仅接收方需要 parseMsg；纯发送方可省略。 */
    public static MsgPayload parseMsg(String msg) {
        MsgPayload payload = JSON.parseObject(msg).toJavaObject(MsgPayload.class);
        validate(payload);
        return payload;
    }

    @Override public String getMQName() { return MQ_NAME; }
    @Override public MQSendType getMQType() { return MQSendType.QUEUE; } // 广播用 BROADCAST
    @Override public String getMessage() { validate(msgPayload); return JSON.toJSONString(msgPayload); }

    public interface MQReceiver {
        void receive(MsgPayload payload);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgPayload {
        @JSONField(name = "biz_id")
        private String bizId;
        // 字段名使用 snake_case（与 Python 端对齐），Java 字段使用 camelCase
    }

    private static void validate(MsgPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("your_event payload is missing");
        }
        if (!StringUtils.hasText(payload.getBizId())) {
            throw new IllegalArgumentException("your_event biz_id is missing");
        }
    }
}
```

### 4.3 命名约定

- `MQ_NAME`（Topic/Queue）：`tolink.<domain>.<event_name>`，使用稳定小写下划线，例如 `tolink.rag.parse_task`。
- Topic 名称变更是**破坏性变更**，需同步 Python 端与运维配置。
- Payload 字段名：`@JSONField(name = "snake_case")`，Java 变量用 camelCase。
- Payload 只放业务最小必要字段，不放大对象、不放文件二进制内容。

## 5. 实现消费者

消费者实现 `XxxMQ.MQReceiver` 接口，由 `KafkaMQTopologyScanner` / `RabbitMQTopologyScanner` 自动发现并绑定：

```java
@Component
@RequiredArgsConstructor
public class YourEventConsumer implements YourEventMQ.MQReceiver {

    private final YourService yourService;

    @Override
    public void receive(YourEventMQ.MsgPayload payload) {
        yourService.handle(payload);
    }
}
```

若需要直接操作原始字符串（如特殊错误处理），实现 `MQMsgReceiver`：

```java
@Component
public class RawConsumer implements MQMsgReceiver {
    @Override
    public void receive(String msg) { ... }
}
```

## 6. 配置项

配置前缀：`tolink.mq`（`MQProperties`）

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `tolink.mq.vender` / `tolink.mq.vendor` | 厂商选择：`rabbitMQ` 或 `kafka` | — |
| `tolink.mq.scanBasePackages` | 扫描消息模型的包路径 | `com.qingluo` |
| `tolink.mq.kafkaAutoCreateTopics` | 启动时是否自动创建 Kafka Topics | `true` |
| `tolink.mq.kafkaTopicPartitions` | Kafka Topic 默认分区数 | `1` |
| `tolink.mq.kafkaTopicReplicas` | Kafka Topic 默认副本数 | `1` |
| `tolink.mq.delayedExchangeName` | RabbitMQ 延迟交换机名称 | `delayExchange` |
| `tolink.mq.fanoutExchangeNamePrefix` | RabbitMQ 广播交换机前缀 | `fanout_exchange_` |

## 7. 交付后同步

新增或修改消息模型后必须：

1. 更新 `docs/reference/mq_contracts.md` 的消息清单与字段说明
2. 更新本 Skill 第 2 节"当前消息清单"
3. 若涉及 Python 端，通知 Python 侧同步消费/发送逻辑
