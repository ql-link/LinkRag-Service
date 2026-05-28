package com.qingluo.link.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时调度。
 *
 * <p>本项目此前无任何 {@code @Scheduled} 任务，故在此集中开启调度，
 * 供卡住任务扫描（{@link com.qingluo.link.service.impl.know.KnowledgeParseStuckScanner}）使用。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
