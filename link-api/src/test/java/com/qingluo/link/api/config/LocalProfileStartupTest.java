package com.qingluo.link.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Context 启动测试（local profile）
 *
 * <p>验证使用 local profile 时 Spring 上下文能正常加载，
 * 确保 H2 内存数据库、MQ=none、OSS=local 等本地配置组合下
 * 所有 Bean 能正常初始化，无 BeanCreationException。</p>
 *
 * <p>Validates: Requirements 2.7, 6.5</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalProfileStartupTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void applicationNameIsConfigured() {
        String appName = context.getEnvironment().getProperty("spring.application.name");
        assertThat(appName).isNotBlank();
    }

    @Test
    void localProfileIsActive() {
        String[] activeProfiles = context.getEnvironment().getActiveProfiles();
        assertThat(activeProfiles).contains("local");
    }
}
