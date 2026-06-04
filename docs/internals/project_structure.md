# Project Structure

toLink-Service 是 Maven 多模块项目。

```text
toLink-Service/
├── link-model       # Entity / DTO / Enum / Result
├── link-core        # Exception / AuthContext / util / global config
├── link-components  # Redis / MQ / OSS components
├── link-mapper      # MyBatis-Plus Mapper
├── link-service     # Business services
├── link-api         # Controllers and Spring Boot application
├── docs             # Docs, contracts, historical module files
├── .ai              # AI skills and prompt source
├── .claude          # Claude-facing links and doc-sync rules
├── .agent           # Agent-facing skill link
└── scripts          # AI/documentation validation scripts
```

依赖方向以业务层复用模型、Mapper、组件为主：`link-api` 调用 `link-service`，`link-service` 组合 `link-mapper`、`link-components`、`link-core`、`link-model`。`link-service` 另引入 `spring-boot-starter-actuator`（Micrometer 监控指标，向上传递给 `link-api`）与测试域 `spring-kafka-test`（EmbeddedKafka 集成测试）。召回网关（recall-gateway）为 `link-service` 引入 `okhttp`（流式调用 Python 内部召回）与 `guava`（按用户限流），为 `link-model` 引入 `jackson-annotations`（请求 DTO 拒绝未知字段）；召回相关类分布于各层（`link-api` 的 `RecallController`、`link-service` 的 `recall/*` 与 `config/Recall*`、`link-core` 的 `security/InternalJwtSigner`）。

## 构建与测试

根 `pom.xml` 统一配置 Maven Surefire `2.22.2`，使各模块 JUnit 5 测试按相同方式执行。提交前运行：

```bash
mvn clean test
```
