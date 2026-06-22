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

依赖方向以业务层复用模型、Mapper、组件为主：`link-api` 调用 `link-service`，`link-service` 组合 `link-mapper`、`link-components`、`link-core`、`link-model`。`link-service` 另引入 `spring-boot-starter-actuator`（Micrometer 监控指标，向上传递给 `link-api`）。召回当前为「前端直连 Python」模式，Java 仅签发 session token：相关类有 `link-api` 的 `RecallSessionController`、`link-service` 的 `recall/RecallSessionServiceImpl` 与 `recall/RecallScopeResolver`、`config/RecallProperties`、`config/RecallExecutorConfig`、`link-core` 的 `security/RecallSessionJwtSigner`。旧召回网关链路（Java 中转代理 Python 内部召回）已于 LINK-122 废弃移除。Java 侧保留一个轻量 LLM 调用例外：`link-service` 使用 `okhttp` + `fastjson` 通过用户 Chat 配置调用 OpenAI-compatible chat completions，为首轮对话异步生成自然标题。

## 构建与测试

根 `pom.xml` 统一配置 Maven Surefire `2.22.2`，使各模块 JUnit 5 测试按相同方式执行。提交前运行：

```bash
mvn clean test
```
