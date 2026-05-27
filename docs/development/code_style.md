# Code Style

## Java

- 遵循当前代码风格：Spring Boot、Lombok、MyBatis-Plus。
- Controller 保持薄层，业务判断放 Service。
- Redis/MQ/OSS 能力优先复用 `link-components` 和 `link-service` 中已有封装。
- 异常优先使用 `link-core` 现有异常体系和 `ErrorCode`。
- 注释只解释复杂业务判断、状态流转、跨组件协作原因。

## 文档

- 新需求使用 `brief.md + acceptance.feature + technical_design.md`。
- 不再新增 `requirement.md` 或 `testing_delivery.md`。
- 不在文档中复制大段代码；引用路径和行为即可。
