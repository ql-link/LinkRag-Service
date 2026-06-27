package com.qingluo.link.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.core.config.ThreadPoolConfig;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池配置测试，覆盖 S17（独立专用池 / 无 customThreadPool）、S18（嵌套 key 绑定）、S20（非法配置启动失败）。
 */
class ThreadPoolConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(ThreadPoolConfig.class);

    @Test
    @DisplayName("S17/S18 加载业务专用池，且无通用 customThreadPool")
    void loadsDedicatedDocumentUploadExecutor() {
        runner.withPropertyValues(
                "thread-pool.document-upload.core-pool-size=5",
                "thread-pool.document-upload.max-pool-size=10",
                "thread-pool.document-upload.queue-capacity=50",
                "thread-pool.document-upload.keep-alive-seconds=60",
                "thread-pool.document-upload.thread-name-prefix=document-file-upload-")
            .run(ctx -> {
                assertThat(ctx).hasBean("documentUploadExecutor");
                assertThat(ctx).doesNotHaveBean("conversationTitleExecutor");
                assertThat(ctx).doesNotHaveBean("customThreadPool");
                Executor executor = (Executor) ctx.getBean("documentUploadExecutor");
                assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
                assertThat(((ThreadPoolTaskExecutor) executor).getThreadNamePrefix())
                    .isEqualTo("document-file-upload-");
            });
    }

    @Test
    @DisplayName("S20 非法配置（max < core）→ 上下文启动失败")
    void invalidConfigFailsStartup() {
        runner.withPropertyValues(
                "thread-pool.document-upload.core-pool-size=10",
                "thread-pool.document-upload.max-pool-size=5")
            .run(ctx -> assertThat(ctx).hasFailed());
    }
}
