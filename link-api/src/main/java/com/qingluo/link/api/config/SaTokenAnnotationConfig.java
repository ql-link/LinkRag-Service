package com.qingluo.link.api.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables Sa-Token method and class annotations on MVC handlers.
 */
@Configuration
public class SaTokenAnnotationConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor().isAnnotation(true))
            .addPathPatterns("/**");
    }
}
