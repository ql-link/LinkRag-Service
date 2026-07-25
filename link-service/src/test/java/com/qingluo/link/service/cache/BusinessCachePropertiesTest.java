package com.qingluo.link.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BusinessCachePropertiesTest {

    @Test
    void defaultOwnerTtlsStayWithinOneDayToOneWeek() {
        BusinessCacheProperties properties = new BusinessCacheProperties();

        assertThat(properties.getDatasetParseConfigTtl())
            .isBetween(Duration.ofDays(1), Duration.ofDays(7));
        assertThat(properties.getUserProfileTtl())
            .isBetween(Duration.ofDays(1), Duration.ofDays(7));
        assertThat(properties.getBlogPublishedIndexTtl())
            .isBetween(Duration.ofDays(1), Duration.ofDays(7));
        assertThat(properties.getBlogPublishedIndexCapacity()).isEqualTo(100);
    }
}
