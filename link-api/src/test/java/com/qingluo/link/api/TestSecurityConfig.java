package com.qingluo.link.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 测试安全配置
 * <p>
 * 配合 sa-token 使用，放行所有路径让 sa-token 处理认证
 * </p>
 */
@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeRequests()
            .anyRequest().permitAll();
        return http.build();
    }
}
