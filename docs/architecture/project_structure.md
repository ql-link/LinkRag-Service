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

依赖方向以业务层复用模型、Mapper、组件为主：`link-api` 调用 `link-service`，`link-service` 组合 `link-mapper`、`link-components`、`link-core`、`link-model`。

## 构建与测试

根 `pom.xml` 统一配置 Maven Surefire `2.22.2`，使各模块 JUnit 5 测试按相同方式执行。提交前运行：

```bash
mvn clean test
```
