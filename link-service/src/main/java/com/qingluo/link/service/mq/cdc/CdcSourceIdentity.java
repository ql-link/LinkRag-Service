package com.qingluo.link.service.mq.cdc;

public record CdcSourceIdentity(String topic, int partition, long offset) {
}
