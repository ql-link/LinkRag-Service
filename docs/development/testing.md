# Testing

## 测试分层

| 类型 | 位置 | 说明 |
| --- | --- | --- |
| 单元测试 | 各模块 `src/test/java` | 使用 JUnit/Mockito，隔离外部依赖 |
| Controller 测试 | `link-api/src/test/java` | 使用 MockMvc 或 Spring 测试配置 |
| Service 测试 | `link-service/src/test/java` | 校验业务分支、事务边界、Mapper/MQ/缓存交互 |
| 组件测试 | `link-components/*/src/test/java` | 校验 Redis/MQ/OSS 组件边界 |

## 命令

```bash
mvn clean test
mvn -pl link-api test
mvn -pl link-service test
```

## 执行约定

- 根 `pom.xml` 固定 Maven Surefire `2.22.2`，确保各模块的 JUnit 5 测试都会执行。
- 提交前使用 `mvn clean test`；删除或移动 Java 类后，干净构建可避免旧 `target/classes` 影响结果。

## Spec-as-Test 要求

- `acceptance.feature` 中的每个 Scenario 必须在 TD 中映射到测试。
- Java 测试不必逐字使用 Gherkin 名称，但测试方法、注释或 TD 映射必须能追溯。
- 外部 MySQL、Redis、Kafka、MinIO、第三方 API 在单元测试中默认 Mock。
- 缓存一致性变更必须分别测试读/回填故障的可用性降级，以及同步删缓存失败的错误传播，不能用降级行为掩盖写路径一致性失败。
- 解析链路测试需覆盖 schema 初始化、扁平 MQ 契约、上传初始化 `document_parse_file`、解析投递事务回滚、重复提交拦截、结果归属校验和 SSE 转发；Java 端不应在结果消费测试中回写 Python 负责的终态字段。
- parse_result 消费兜底测试：失败分类与当前任务过滤用 Mockito 单测（`DocumentParseResultServiceImplTest`、`DocumentParseStuckScannerTest`、`ParseResultKafkaConfigTest`）；“坏消息不阻塞后续 / 缓存补偿隔离”用 `@EmbeddedKafka` 集成测试（`ParseResultConsumerEmbeddedKafkaTest`，按 `*Test` 命名以纳入 Surefire）。所有用例须断言 Java 不写业务表（`verify(...never()).updateById`）。
