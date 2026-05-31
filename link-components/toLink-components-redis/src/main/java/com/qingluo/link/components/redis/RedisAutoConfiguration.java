package com.qingluo.link.components.redis;

import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis 组件自动配置
 */
@Configuration
@EnableConfigurationProperties(CacheConsistencyProperties.class)
@ComponentScan("com.qingluo.link.components.redis.service")
public class RedisAutoConfiguration {

    @Bean
    public CommandLineRunner initRedisUtils(RedisTemplate<String, Object> redisTemplate) {
        return args -> RedisUtils.setRedisTemplate(redisTemplate);
    }
}
