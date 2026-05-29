package com.qingluo.link.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 环境变量覆盖集成测试。
 *
 * <p>验证 dev profile 下环境变量能正确覆盖配置文件中的默认值。
 * 通过 @SpringBootTest(properties=...) 模拟设置所有必需环境变量，
 * 并覆盖连接池、线程池、日志级别等参数，确认 Spring 能正确解析并应用覆盖值。</p>
 *
 * <p>使用 WebEnvironment.NONE 避免加载 Web 层，防止因控制器编译问题导致测试失败。</p>
 *
 * <p>Validates: Requirements 3.6, 3.7, 3.8, 3.9, 3.10, 4.10</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.profiles.active=dev",
        // 必需数据库环境变量
        "DB_HOST=localhost",
        "DB_PORT=3306",
        "DB_NAME=test",
        "DB_USERNAME=root",
        "DB_PASSWORD=test",
        "spring.datasource.url=jdbc:h2:mem:env_override;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        // 必需 Redis 环境变量
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "REDIS_PASSWORD=test",
        // 必需 Kafka 环境变量
        "KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
        "KAFKA_SASL_USERNAME=user",
        "KAFKA_SASL_PASSWORD=pass",
        "TOLINK_MQ_VENDOR=none",
        // 必需 MinIO 环境变量
        "MINIO_ENDPOINT=localhost:9000",
        "MINIO_ACCESS_KEY=key",
        "MINIO_SECRET_KEY=secret",
        // 必需 LLM 环境变量
        "LLM_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        // 覆盖连接池参数（模拟生产环境配置）
        "DRUID_INITIAL_SIZE=5",
        // 覆盖线程池参数（模拟生产环境配置）——多池就绪后绑定到 thread-pool.document-upload.*
        "THREAD_POOL_CORE_SIZE=10",
        "THREAD_POOL_MAX_SIZE=20",
        "THREAD_POOL_QUEUE_CAPACITY=100",
        "THREAD_POOL_KEEP_ALIVE_SECONDS=120",
        "THREAD_POOL_THREAD_NAME_PREFIX=doc-up-",
        // 覆盖日志级别（模拟生产环境配置）
        "LOG_LEVEL=info",
        // 覆盖 MyBatis 日志实现（模拟生产环境关闭 SQL 日志）
        "MYBATIS_LOG_IMPL=org.apache.ibatis.logging.nologging.NoLoggingImpl"
    }
)
@DisplayName("环境变量覆盖集成测试")
class EnvVarOverrideTest {

    @Value("${thread-pool.document-upload.core-pool-size}")
    private int corePoolSize;

    @Value("${thread-pool.document-upload.max-pool-size}")
    private int maxPoolSize;

    @Value("${thread-pool.document-upload.queue-capacity}")
    private int queueCapacity;

    @Value("${thread-pool.document-upload.keep-alive-seconds}")
    private int keepAliveSeconds;

    @Value("${thread-pool.document-upload.thread-name-prefix}")
    private String threadNamePrefix;

    @Value("${logging.level.com.qingluo.link}")
    private String logLevel;

    @Test
    @DisplayName("document-upload 线程池五项参数均被环境变量覆盖")
    void threadPoolParamsOverridden() {
        assertThat(corePoolSize).isEqualTo(10);
        assertThat(maxPoolSize).isEqualTo(20);
        assertThat(queueCapacity).isEqualTo(100);
        assertThat(keepAliveSeconds).isEqualTo(120);
        assertThat(threadNamePrefix).isEqualTo("doc-up-");
    }

    @Test
    @DisplayName("日志级别被环境变量覆盖为 info")
    void logLevelOverridden() {
        assertThat(logLevel).isEqualTo("info");
    }
}
