package com.qingluo.link.service.impl;

import com.qingluo.link.mapper.UserLoginEventMapper;
import com.qingluo.link.model.dto.entity.UserLoginEvent;
import com.qingluo.link.service.UserLoginEventRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
public class UserLoginEventRecorderImpl implements UserLoginEventRecorder {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserLoginEventMapper userLoginEventMapper;
    private final Clock userStatisticsClock;

    @Autowired
    public UserLoginEventRecorderImpl(UserLoginEventMapper userLoginEventMapper) {
        this(userLoginEventMapper, Clock.systemUTC());
    }

    UserLoginEventRecorderImpl(UserLoginEventMapper userLoginEventMapper, Clock userStatisticsClock) {
        this.userLoginEventMapper = userLoginEventMapper;
        this.userStatisticsClock = userStatisticsClock;
    }

    @Override
    public void record(Long userId, String source) {
        try {
            UserLoginEvent event = new UserLoginEvent();
            event.setUserId(userId);
            event.setLoginSource(source);
            event.setCreatedAt(LocalDateTime.ofInstant(userStatisticsClock.instant(), BUSINESS_ZONE));
            userLoginEventMapper.insert(event);
        } catch (RuntimeException e) {
            // 统计旁路不能降低认证可用性；失败由日志告警并允许活跃数少计。
            log.error("Failed to persist user login event, userId={}, source={}", userId, source, e);
        }
    }
}
