package com.qingluo.link.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("dev profile 配置隔离")
class DevProfileConfigurationTest {

    @Test
    @DisplayName("dev 默认连接独立数据库和 MinIO bucket")
    void devDefaultsTargetIsolatedResources() throws IOException, URISyntaxException {
        Path resource = Path.of(requireNonNull(
            getClass().getClassLoader().getResource("application-dev.yml")
        ).toURI());
        String source = Files.readString(resource);

        assertThat(source)
            .contains("${DB_NAME:tolink_rag_dev}")
            .contains("${MINIO_RAW_BUCKET:tolink-dev-raw}")
            .contains("${MINIO_PRIVATE_BUCKET:tolink-dev-docs}")
            .contains("${MINIO_PUBLIC_BUCKET:tolink-dev-public}")
            .doesNotContain("tolink_rag_test")
            .doesNotContain("tolink-test-");
    }

    @Test
    @DisplayName("local、dev、prod 默认只启用 RabbitMQ")
    void profilesDefaultToRabbitMqWithKafkaDisabled()
        throws IOException, URISyntaxException {
        for (String profile : new String[]{"local", "dev", "prod"}) {
            Path resource = Path.of(requireNonNull(
                getClass().getClassLoader().getResource("application-" + profile + ".yml")
            ).toURI());
            String source = Files.readString(resource);

            assertThat(source)
                .contains("TOLINK_MQ_VENDER:rabbitMQ")
                .contains("kafka-auto-create-topics: false")
                .doesNotContain("KAFKA_LISTENER_AUTO_STARTUP:true");
            if ("local".equals(profile)) {
                assertThat(source).contains("auto-startup: false");
            } else {
                assertThat(source).contains("KAFKA_LISTENER_AUTO_STARTUP:false");
            }
        }
    }

    private static <T> T requireNonNull(T value) {
        if (value == null) {
            throw new IllegalStateException("application-dev.yml not found");
        }
        return value;
    }
}
