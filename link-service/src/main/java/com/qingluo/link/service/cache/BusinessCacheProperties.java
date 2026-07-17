package com.qingluo.link.service.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tolink.business-cache")
public class BusinessCacheProperties {

    private boolean enabled = true;
    private Duration datasetParseConfigTtl = Duration.ofDays(7);
    private Duration userProfileTtl = Duration.ofDays(1);
    private Duration blogPublishedIndexTtl = Duration.ofDays(1);
    private int blogPublishedIndexCapacity = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDatasetParseConfigTtl() {
        return datasetParseConfigTtl;
    }

    public void setDatasetParseConfigTtl(Duration datasetParseConfigTtl) {
        this.datasetParseConfigTtl = datasetParseConfigTtl;
    }

    public Duration getUserProfileTtl() {
        return userProfileTtl;
    }

    public void setUserProfileTtl(Duration userProfileTtl) {
        this.userProfileTtl = userProfileTtl;
    }

    public Duration getBlogPublishedIndexTtl() {
        return blogPublishedIndexTtl;
    }

    public void setBlogPublishedIndexTtl(Duration blogPublishedIndexTtl) {
        this.blogPublishedIndexTtl = blogPublishedIndexTtl;
    }

    public int getBlogPublishedIndexCapacity() {
        return blogPublishedIndexCapacity;
    }

    public void setBlogPublishedIndexCapacity(int blogPublishedIndexCapacity) {
        this.blogPublishedIndexCapacity = blogPublishedIndexCapacity;
    }
}
