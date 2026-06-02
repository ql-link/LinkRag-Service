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

## 验证

```bash
mvn test
python3 scripts/check_docs_sync.py --working
```
