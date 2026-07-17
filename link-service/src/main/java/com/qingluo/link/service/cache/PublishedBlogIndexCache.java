package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheKeyRouter;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublishedBlogIndexCache {

    private final CacheReadProtectionService readProtectionService;
    private final CacheConsistencyService consistencyService;
    private final CacheKeyRouter keyRouter;
    private final BusinessCacheProperties properties;
    private final BusinessCacheReadiness readiness;

    public PublishedBlogIndexSnapshot get(Supplier<PublishedBlogIndexSnapshot> loader) {
        if (!readiness.isDatabaseMirrorCacheEnabled()) {
            return loader.get();
        }
        return readProtectionService.getOrLoad(
            keyRouter.route(CacheEvictTarget.PUBLISHED_BLOG_INDEX, "global"),
            PublishedBlogIndexSnapshot.class,
            properties.getBlogPublishedIndexTtl().toSeconds(),
            TimeUnit.SECONDS,
            loader);
    }

    public void evict() {
        consistencyService.evict(CacheEvictTarget.PUBLISHED_BLOG_INDEX, "global");
    }
}
