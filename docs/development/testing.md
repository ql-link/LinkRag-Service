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
mvn test
mvn -pl link-api test
mvn -pl link-service test
```

## Spec-as-Test 要求

- `acceptance.feature` 中的每个 Scenario 必须在 TD 中映射到测试。
- Java 测试不必逐字使用 Gherkin 名称，但测试方法、注释或 TD 映射必须能追溯。
- 外部 MySQL、Redis、Kafka、MinIO、第三方 API 在单元测试中默认 Mock。
