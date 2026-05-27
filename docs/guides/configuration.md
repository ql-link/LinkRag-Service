# Configuration

配置事实来源：`link-api/src/main/resources/application.yml`。

## 主要配置组

| 配置 | 说明 |
| --- | --- |
| `spring.datasource.*` | MySQL 连接 |
| `spring.redis.*` | Redis 连接 |
| `spring.kafka.*` | Kafka 生产/消费配置 |
| `sa-token.*` | 登录态与 token 配置 |
| `llm.api-key.secret` | API Key 加密密钥 |
| `qingluopay.mq.*` | MQ 组件供应商、扫描包、Kafka topic 创建 |
| `tolink.oss.*` | 本地/MinIO/阿里云 OSS 参数 |
| `tolink.knowledge-file.*` | 知识文件大小、内部访问、允许后缀 |
| `thread-pool.*` | 知识文件相关线程池 |

敏感值应通过环境变量注入，不应提交真实生产密钥。
