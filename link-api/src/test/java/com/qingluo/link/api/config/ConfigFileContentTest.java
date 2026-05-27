package com.qingluo.link.api.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置文件内容校验单元测试。
 * 验证配置文件遵循分层架构约束：
 * - application.yml 不包含环境相关配置和敏感值
 * - application-dev.yml 不包含硬编码密码、真实 IP 和废弃别名
 * - application-dev.yml 正确使用环境变量引用
 *
 * Validates: Requirements 1.2, 1.4, 3.1, 3.7, 3.8, 3.9, 3.10, 4.6
 */
@DisplayName("配置文件内容校验")
class ConfigFileContentTest {

    /** 主配置文件目录相对于项目根目录的路径 */
    private static final String MAIN_RESOURCES_DIR = "src/main/resources";

    private static String baseConfigContent;
    private static String devConfigContent;

    @BeforeAll
    static void loadConfigFiles() throws IOException {
        baseConfigContent = readMainResourceFile("application.yml");
        devConfigContent = readMainResourceFile("application-dev.yml");
    }

    /**
     * 从 src/main/resources 目录读取配置文件。
     * 不使用 classpath 加载，避免 test resources 中同名文件的遮蔽问题。
     */
    private static String readMainResourceFile(String filename) throws IOException {
        // 尝试从项目模块根目录定位 src/main/resources
        Path resourcePath = resolveMainResourcePath(filename);
        return Files.readString(resourcePath, StandardCharsets.UTF_8);
    }

    private static Path resolveMainResourcePath(String filename) {
        // Maven 测试运行时 user.dir 通常指向模块根目录（link-api）
        Path moduleRoot = Paths.get(System.getProperty("user.dir"));
        Path candidate = moduleRoot.resolve(MAIN_RESOURCES_DIR).resolve(filename);
        if (Files.exists(candidate)) {
            return candidate;
        }
        // 如果当前目录是项目根目录，尝试 link-api 子目录
        candidate = moduleRoot.resolve("link-api").resolve(MAIN_RESOURCES_DIR).resolve(filename);
        if (Files.exists(candidate)) {
            return candidate;
        }
        throw new IllegalStateException(
                "Cannot find " + filename + " in src/main/resources. " +
                "Searched from: " + moduleRoot.toAbsolutePath());
    }

    @Nested
    @DisplayName("application.yml 公共基础配置约束")
    class BaseConfigTests {

        @Test
        @DisplayName("不包含 datasource 配置")
        void shouldNotContainDatasource() {
            assertThat(baseConfigContent).doesNotContain("datasource:");
        }

        @Test
        @DisplayName("不包含 redis 配置")
        void shouldNotContainRedis() {
            assertThat(baseConfigContent).doesNotContain("redis:");
        }

        @Test
        @DisplayName("不包含 kafka 配置")
        void shouldNotContainKafka() {
            assertThat(baseConfigContent).doesNotContain("kafka:");
        }

        @Test
        @DisplayName("不包含真实 IP 地址模式（ip:port）")
        void shouldNotContainRealIpAddress() {
            assertThat(baseConfigContent).doesNotContainPattern("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+");
        }

        @Test
        @DisplayName("不包含已知密码")
        void shouldNotContainKnownPasswords() {
            assertThat(baseConfigContent).doesNotContain("ql354210");
        }
    }

    @Nested
    @DisplayName("application-dev.yml 部署配置约束")
    class DevConfigTests {

        @Test
        @DisplayName("不包含硬编码密码 ql354210")
        void shouldNotContainHardcodedPassword() {
            assertThat(devConfigContent).doesNotContain("ql354210");
        }

        @Test
        @DisplayName("不包含真实 IP 43.138.176.52")
        void shouldNotContainRealIp1() {
            assertThat(devConfigContent).doesNotContain("43.138.176.52");
        }

        @Test
        @DisplayName("不包含真实 IP 36.213.180.176")
        void shouldNotContainRealIp2() {
            assertThat(devConfigContent).doesNotContain("36.213.180.176");
        }

        @Test
        @DisplayName("不包含废弃别名 DB_USER（区别于 DB_USERNAME）")
        void shouldNotContainDeprecatedDbUser() {
            // DB_USER 是废弃别名，DB_USERNAME 是新标准名称
            // 使用正则确保不存在独立的 DB_USER 引用（后面不跟 NAME）
            assertThat(devConfigContent).doesNotContainPattern("\\$\\{DB_USER[^N]");
            assertThat(devConfigContent).doesNotContainPattern("\\$\\{DB_USER\\}");
        }

        @Test
        @DisplayName("不包含废弃别名 KAFKA_USERNAME（区别于 KAFKA_SASL_USERNAME）")
        void shouldNotContainDeprecatedKafkaUsername() {
            // KAFKA_USERNAME 是废弃别名，KAFKA_SASL_USERNAME 是新标准名称
            // 确保不存在 ${KAFKA_USERNAME} 或 ${KAFKA_USERNAME:...} 形式的引用
            assertThat(devConfigContent).doesNotContainPattern("\\$\\{KAFKA_USERNAME[}:]");
        }

        @Test
        @DisplayName("不包含废弃别名 KAFKA_PASSWORD（区别于 KAFKA_SASL_PASSWORD）")
        void shouldNotContainDeprecatedKafkaPassword() {
            // KAFKA_PASSWORD 是废弃别名，KAFKA_SASL_PASSWORD 是新标准名称
            assertThat(devConfigContent).doesNotContainPattern("\\$\\{KAFKA_PASSWORD[}:]");
        }

        @Test
        @DisplayName("不包含废弃别名 OSS_MINIO_ 前缀")
        void shouldNotContainDeprecatedOssMinio() {
            assertThat(devConfigContent).doesNotContain("OSS_MINIO_");
        }

        @Test
        @DisplayName("不包含废弃别名 OSS_ALIYUN_ 前缀")
        void shouldNotContainDeprecatedOssAliyun() {
            assertThat(devConfigContent).doesNotContain("OSS_ALIYUN_");
        }
    }

    @Nested
    @DisplayName("application-dev.yml 环境变量引用验证")
    class DevConfigEnvVarTests {

        @Test
        @DisplayName("包含 Druid 连接池环境变量 DRUID_INITIAL_SIZE:0")
        void shouldContainDruidInitialSize() {
            assertThat(devConfigContent).contains("${DRUID_INITIAL_SIZE:0}");
        }

        @Test
        @DisplayName("包含 Druid 连接池环境变量 DRUID_MIN_IDLE:0")
        void shouldContainDruidMinIdle() {
            assertThat(devConfigContent).contains("${DRUID_MIN_IDLE:0}");
        }

        @Test
        @DisplayName("包含 Druid 连接池环境变量 DRUID_MAX_ACTIVE:8")
        void shouldContainDruidMaxActive() {
            assertThat(devConfigContent).contains("${DRUID_MAX_ACTIVE:8}");
        }

        @Test
        @DisplayName("包含线程池环境变量 THREAD_POOL_CORE_SIZE:5")
        void shouldContainThreadPoolCoreSize() {
            assertThat(devConfigContent).contains("${THREAD_POOL_CORE_SIZE:5}");
        }

        @Test
        @DisplayName("包含线程池环境变量 THREAD_POOL_MAX_SIZE:10")
        void shouldContainThreadPoolMaxSize() {
            assertThat(devConfigContent).contains("${THREAD_POOL_MAX_SIZE:10}");
        }

        @Test
        @DisplayName("包含线程池环境变量 THREAD_POOL_QUEUE_CAPACITY:50")
        void shouldContainThreadPoolQueueCapacity() {
            assertThat(devConfigContent).contains("${THREAD_POOL_QUEUE_CAPACITY:50}");
        }

        @Test
        @DisplayName("包含日志级别环境变量 LOG_LEVEL:debug")
        void shouldContainLogLevel() {
            assertThat(devConfigContent).contains("${LOG_LEVEL:debug}");
        }

        @Test
        @DisplayName("包含 MyBatis 日志实现环境变量 MYBATIS_LOG_IMPL:")
        void shouldContainMybatisLogImpl() {
            assertThat(devConfigContent).contains("${MYBATIS_LOG_IMPL:");
        }
    }
}
