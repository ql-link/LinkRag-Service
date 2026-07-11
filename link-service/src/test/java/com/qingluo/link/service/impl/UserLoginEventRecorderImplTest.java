package com.qingluo.link.service.impl;

import com.qingluo.link.mapper.UserLoginEventMapper;
import com.qingluo.link.model.dto.entity.UserLoginEvent;
import com.qingluo.link.service.UserLoginEventRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserLoginEventRecorderImplTest {
    @Mock
    private UserLoginEventMapper mapper;

    @Test
    void Should_PersistShanghaiTimestamp_When_RecordLoginSuccess() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T16:00:00Z"), ZoneOffset.UTC);
        UserLoginEventRecorderImpl recorder = new UserLoginEventRecorderImpl(mapper, clock);

        recorder.record(7L, UserLoginEventRecorder.SOURCE_LOGIN);

        ArgumentCaptor<UserLoginEvent> captor = ArgumentCaptor.forClass(UserLoginEvent.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getLoginSource()).isEqualTo("LOGIN");
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-07-12T00:00:00"));
    }

    @Test
    void Should_NotThrow_When_EventInsertFails() {
        doThrow(new RuntimeException("db unavailable")).when(mapper).insert(any());
        UserLoginEventRecorderImpl recorder = new UserLoginEventRecorderImpl(mapper, Clock.systemUTC());

        assertThatCode(() -> recorder.record(7L, UserLoginEventRecorder.SOURCE_LOGIN)).doesNotThrowAnyException();
    }
}
