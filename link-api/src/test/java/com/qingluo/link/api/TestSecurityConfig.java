package com.qingluo.link.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.core.annotation.Order;

/**
 * 测试环境安全配置
 *
 * <h2>配置目的</h2>
 * <p>
 * 在集成测试环境中禁用 Spring Security 的默认安全检查，
 * 让 sa-token 作为唯一的认证/授权处理组件。
 * </p>
 *
 * <h2>配置说明</h2>
 * <ul>
 *   <li><b>csrf().disable()</b>: 禁用 CSRF 防护（测试环境不需要）</li>
 *   <li><b>anyRequest().permitAll()</b>: 放行所有请求（由 sa-token 处理认证）</li>
 * </ul>
 *
 * <h2>为什么需要这个配置？</h2>
 * <p>
 * 项目使用 sa-token 进行认证，而 sa-token 有自己的拦截器处理登录状态验证。
 * 如果不禁用 Spring Security，它的默认拦截器会先于 sa-token 执行，
 * 导致所有请求被 Spring Security 拦截（返回 403 或 401）。
 * </p>
 *
 * <h2>认证流程（测试环境）</h2>
 * <pre>
 * HTTP 请求
 *     ↓
 * Spring Security FilterChain (permitAll - 放行)
 *     ↓
 * sa-token Filter (验证 satoken header)
 *     ↓
 * Controller
 * </pre>
 *
 * <h2>安全说明</h2>
 * <p>
 * 此配置仅适用于测试环境！生产环境不应禁用安全检查。
 * 测试中验证 401/403 等响应码的功能，已在单独的认证测试中覆盖。
 * </p>
 *
 * @see <a href="https://sa-token.cc/">sa-token 文档</a>
 * @author Claude Code
 * @since 2026-04-14
 */
@TestConfiguration
@EnableWebSecurity
@Order(1)
public class TestSecurityConfig extends WebSecurityConfigurerAdapter {

    /**
     * 测试环境安全过滤器链
     *
     * @throws Exception 配置异常
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // ===== 禁用 CSRF 防护 =====
            // 测试环境不需要 CSRF token，API 测试使用 header 传递 token
            .csrf().disable()

            // ===== 授权配置 =====
            .authorizeRequests()
            // 放行所有请求（让 sa-token 处理认证）
            .anyRequest().permitAll();
    }
}
