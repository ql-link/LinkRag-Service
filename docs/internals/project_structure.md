# Project Structure

toLink-Service 是 Maven 多模块项目。

```text
toLink-Service/
├── link-model         # Entity / DTO / Enum / Result
├── link-observability # trace_id / access log / audit log
├── link-core          # Exception / AuthContext / util / global config
├── link-components    # Redis / MQ / OSS components
├── link-mapper        # MyBatis-Plus Mapper
├── link-service       # Business services
├── link-api           # Controllers and Spring Boot application
├── docs               # Docs, contracts, historical module files
├── .ai                # AI skills and prompt source
├── .claude            # Claude-facing links and doc-sync rules
├── .agent             # Agent-facing skill link
└── scripts            # AI/documentation validation scripts
```

依赖方向以业务层复用模型、Mapper、组件为主：`link-api` 调用 `link-service`，`link-service` 组合 `link-mapper`、`link-components`、`link-core`、`link-model`、`link-observability`。`link-observability` 是独立横向模块，承载 `TraceContext` / `TraceIdFilter` / `MdcTaskDecorator` / `TraceHeaders` / `AccessLogFilter` / `AuditLog`，不依赖业务模块；涉及 Spring Service、Loki HTTP 调用和管理端分页 DTO 的日志查询代理放在 `link-service` 的 `observability` 包，由 `link-api` 的 `AdminLogController` 暴露。`link-core` 仅通过 `AuthCurrentUserProvider` 把 `AuthContext` 桥接给访问日志。`link-service` 另引入 `spring-boot-starter-actuator`。Java 登录使用 `AccessTokenJwtSigner` 生成 RS256 JWT，`AuthServiceImpl` 把同一字符串注册为 Sa-Token 登录凭证；旧 recall session 签发与 Java 召回中转均已删除。对话标题由 Python 问答链路生成并随 `chat_turn.title` 上报，Java 仅做条件落库与手动标题保护。

## 构建与测试

根 `pom.xml` 统一配置 Maven Surefire `2.22.2`，使各模块 JUnit 5 测试按相同方式执行；同时在 dependencyManagement 中锁定 OkHttp `4.12.0` / Okio `3.6.0`，避免 Spring Boot 2.5 默认的 OkHttp 3.x 与 MinIO 8.5.x 运行期 API 不兼容；`logstash-logback-encoder` 由 `link-api` 引入，用于 Logback JSON Lines 输出，运行期 trace/access/audit 入口由 `link-observability` 提供。提交前运行：

```bash
mvn clean test
```
