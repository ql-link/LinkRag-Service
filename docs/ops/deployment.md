# Deployment

## 本地启动

```bash
mysql -h <DB_HOST> -u root -p < scripts/db/init.sql
mysql -h <DB_HOST> -u root -p < scripts/db/seed_llm_providers.sql
mvn spring-boot:run -pl link-api
```

## 运行前提

- JDK 17
- Maven
- MySQL 8
- Redis
- Kafka 或 RabbitMQ（默认配置使用 Kafka）
- 本地 OSS 目录或 MinIO

## Jenkins 与容器配置注入

`application-prod.yml` 随代码打入镜像，只保存结构、地址和非敏感参数。生产账号、密码和密钥
长期保存在部署机 `/opt/tolink/toLink-Service/config/application-prod-local.yml`，权限必须为
`600`。Jenkins 每次更新镜像和 Compose，但不覆盖该文件；Compose 只读挂载到
`/app/config/application-prod-local.yml`，由镜像内的 production 配置自动导入。

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

### 日志集中采集（Docker）

仓库提供一套最小 Loki + Promtail 部署文件，用于本机或单机服务器验证 Java/Python JSON Lines 日志采集：

```bash
docker compose -f deploy/docker-compose.observability.yml up -d
curl http://localhost:3100/ready
```

Java 后端的管理端日志模块通过 `OBSERVABILITY_LOKI_BASE_URL` 访问 Loki。生产 Compose 默认使用同一 Docker 网络中的 `http://tolink-loki:3100`；本机单机验证可覆盖为 `http://host.docker.internal:3100`，Java 服务直接跑在宿主机时可使用 `http://localhost:3100`。该地址只给 Java 后端使用，不应让前端直接访问 Loki。

默认挂载路径：

| 服务 | 宿主路径 | 容器内路径 | 说明 |
| --- | --- | --- | --- |
| Java | `./logs` | `/var/log/tolink-service` | 对应 `deploy/docker-compose.yml` 挂载出的 Java `LOG_PATH=/app/logs` |
| Python | `./python-logs` 或 `${PYTHON_LOG_PATH}` | `/var/log/tolink-rag` | 用于挂载 Python 端 Loguru JSON 日志目录 |

Promtail 配置位于 `deploy/observability/promtail-config.yml`：Java 日志按顶层 `service` / `level` / `host` 提取 Loki labels；Python 日志按 `record.extra.service` / `record.level.name` / `record.extra.host` 提取。`trace_id` 不作为 Loki label，避免高基数字段拖垮索引；查询时按日志 JSON 内容过滤，例如：

```bash
curl -G 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={service="tolink-service"} |= "trace_id_value"'
```

Python RAG 服务的日志服务名必须配置为 `LOG_SERVICE_NAME=tolink-rag`。如果 Python 端仍使用默认 `tolink-service`，Promtail/Loki 可以采集到日志，但管理端按 `service` 筛选时无法区分 Java 与 Python。

前端应调用 Java 管理端代理接口而不是 Loki：

```bash
curl 'http://localhost:8080/api/v1/admin/logs?service=tolink-service&page=1&page_size=50' \
  -H 'satoken: <admin-token>'
curl 'http://localhost:8080/api/v1/admin/logs/labels' \
  -H 'satoken: <admin-token>'
```

单行日志字段与 Java/Python 统一追踪约定对齐：`time` / `level` / `service` / `host` / `pid` / `trace_id` / `logger_name` / `message` / `exception`。Java 文件日志为顶层 JSON 字段；Python 当前 Loguru `serialize=True` 文件日志在 `record.extra.*` 下携带 trace 扩展字段，采集侧需分别映射。

### 日志集中采集（Docker）

仓库提供一套最小 Loki + Promtail 部署文件，用于本机或单机服务器验证 Java/Python JSON Lines 日志采集：

```bash
docker compose -f deploy/docker-compose.observability.yml up -d
curl http://localhost:3100/ready
```

Java 后端的管理端日志模块通过 `OBSERVABILITY_LOKI_BASE_URL` 访问 Loki。生产环境的 Java 与 Loki 都位于云服务器 `tolink-app-net`，默认使用 `http://tolink-loki:3100`；本机单机验证可覆盖为 `http://host.docker.internal:3100`，Java 服务直接跑在宿主机时可使用 `http://localhost:3100`。该地址只给 Java 后端使用，不应让前端直接访问 Loki。

默认挂载路径：

| 服务 | 宿主路径 | 容器内路径 | 说明 |
| --- | --- | --- | --- |
| Java | `./logs` | `/var/log/tolink-service` | 对应 `deploy/docker-compose.yml` 挂载出的 Java `LOG_PATH=/app/logs` |
| Python | `./python-logs` 或 `${PYTHON_LOG_PATH}` | `/var/log/tolink-rag` | 用于挂载 Python 端 Loguru JSON 日志目录 |

Promtail 配置位于 `deploy/observability/promtail-config.yml`：Java 日志按顶层 `service` / `level` / `host` 提取 Loki labels；Python 日志按 `record.extra.service` / `record.level.name` / `record.extra.host` 提取。`trace_id` 不作为 Loki label，避免高基数字段拖垮索引；查询时按日志 JSON 内容过滤，例如：

```bash
curl -G 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={service="tolink-service"} |= "trace_id_value"'
```

Python RAG 服务的日志服务名必须配置为 `LOG_SERVICE_NAME=tolink-rag`。如果 Python 端仍使用默认 `tolink-service`，Promtail/Loki 可以采集到日志，但管理端按 `service` 筛选时无法区分 Java 与 Python。

前端应调用 Java 管理端代理接口而不是 Loki：

```bash
curl 'http://localhost:8080/api/v1/admin/logs?service=tolink-service&page=1&page_size=50' \
  -H 'satoken: <admin-token>'
curl 'http://localhost:8080/api/v1/admin/logs/labels' \
  -H 'satoken: <admin-token>'
```

## 验证

```bash
mvn test
python3 scripts/check_docs_sync.py --working
```
