package com.qingluo.link.components.mq.vender.rabbitmq;

import com.qingluo.link.components.mq.AbstractMQ;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Scans business message model classes so topology can be declared from models.
 */
public class RabbitMQTopologyScanner implements ResourceLoaderAware {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQTopologyScanner.class);

    private ResourceLoader resourceLoader;

    public List<AbstractMQ> scan(List<String> basePackages) {
        List<AbstractMQ> abstractMQList = new ArrayList<>();
        if (CollectionUtils.isEmpty(basePackages)) {
            return abstractMQList;
        }

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(AbstractMQ.class));
        if (resourceLoader != null) {
            scanner.setResourceLoader(resourceLoader);
        }

        for (String basePackage : basePackages) {
            if (!StringUtils.hasText(basePackage)) {
                continue;
            }
            scanner.findCandidateComponents(basePackage).forEach(beanDefinition -> {
                String beanClassName = beanDefinition.getBeanClassName();
                if (!StringUtils.hasText(beanClassName)) {
                    return;
                }
                instantiateMessageModel(beanClassName).ifPresent(abstractMQList::add);
            });
        }
        return abstractMQList;
    }

    private java.util.Optional<AbstractMQ> instantiateMessageModel(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!AbstractMQ.class.isAssignableFrom(clazz) || clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of((AbstractMQ) BeanUtils.instantiateClass(clazz));
        } catch (Exception ex) {
            log.warn("Skip MQ model [{}], it must implement AbstractMQ and provide a no-arg constructor", className, ex);
            return java.util.Optional.empty();
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
