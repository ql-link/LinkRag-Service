# Deployment

## 本地启动

```bash
mysql -h <DB_HOST> -u root -p < scripts/db/schema.sql
mysql -h <DB_HOST> -u root -p tolink_rag_db < scripts/db/init.sql
mvn spring-boot:run -pl link-api
```

## 运行前提

- JDK 17
- Maven
- MySQL 8
- Redis
- Kafka 或 RabbitMQ（默认配置使用 Kafka）
- 本地 OSS 目录或 MinIO

## 日志

logback（`link-api/src/main/resources/logback-spring.xml`）按天输出 JSON Lines 到日期文件夹，仅保留最近 7 天：

```
<LOG_PATH>/2026-06-07/tolink-service.log         # 当天全量日志
<LOG_PATH>/2026-06-07/tolink-service-error.log   # 当天 error 日志
```

`LOG_PATH` 默认 `logs`（相对启动工作目录）。Docker 部署下容器工作目录为 `/app`，`deploy/docker-compose.yml` 将容器 `/app/logs` 挂载到宿主 `/opt/tolink/toLink-Service/logs`，日志持久化在宿主机：

```bash
# 宿主机查看
tail -f /opt/tolink/toLink-Service/logs/$(date +%F)/tolink-service.log
# 容器控制台日志（stdout，由 docker 日志驱动接管）
docker logs -f tolink-service
```

单行日志字段与 Java/Python 统一追踪约定对齐：`time` / `level` / `service` / `host` / `pid` / `trace_id` / `logger_name` / `message` / `exception`。Java 文件日志为顶层 JSON 字段；Python 当前 Loguru `serialize=True` 文件日志在 `record.extra.*` 下携带 trace 扩展字段，采集侧需分别映射。

## 验证

```bash
mvn test
python3 scripts/check_docs_sync.py --working
```
