package com.qingluo.link.components.redis;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Redis 组件自动配置
 */
@Configuration
@ComponentScan("com.qingluo.link.components.redis.service")
public class RedisAutoConfiguration {

    @Bean
    public CommandLineRunner initRedisUtils(RedisTemplate<String, Object> redisTemplate) {
        return args -> RedisUtils.setRedisTemplate(redisTemplate);
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("cache-evict-");
        return scheduler;
    }
}
