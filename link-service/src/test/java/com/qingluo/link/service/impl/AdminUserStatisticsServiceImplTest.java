package com.qingluo.link.service.impl;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.mapper.UserLoginEventMapper;
import com.qingluo.link.model.dto.response.AdminUserCountBreakdownDTO;
import com.qingluo.link.model.dto.response.AdminUserDashboardDTO;
import com.qingluo.link.model.dto.response.UserDailyCountDTO;
import com.qingluo.link.service.impl.admin.AdminUserStatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserStatisticsServiceImplTest {
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private UserLoginEventMapper loginEventMapper;

    private AdminUserStatisticsServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-11T04:00:00Z"), ZoneOffset.UTC);
        service = new AdminUserStatisticsServiceImpl(sysUserMapper, loginEventMapper, clock);
    }

    @Test
    void Should_ReturnCompleteDashboardAndFillMissingDates_When_QuerySevenDays() {
        given(sysUserMapper.selectUserBreakdown()).willReturn(new AdminUserCountBreakdownDTO(8, 2, 9, 1));
        given(sysUserMapper.countCreatedBetween(any(), any())).willReturn(4L, 2L);
        given(loginEventMapper.countDistinctUsersBetween(any(), any())).willReturn(6L, 3L);
        given(sysUserMapper.selectDailyCreatedBetween(any(), any()))
                .willReturn(List.of(daily("2026-07-05", 2), daily("2026-07-11", 2)));
        given(loginEventMapper.selectDailyActiveBetween(any(), any()))
                .willReturn(List.of(daily("2026-07-10", 3)));

        AdminUserDashboardDTO result = service.getDashboard(7);

        assertThat(result.getTotalUsers()).isEqualTo(10);
        assertThat(result.getNewUsers().getGrowthRate()).isEqualTo(1.0);
        assertThat(result.getActiveUsers().getGrowthRate()).isEqualTo(1.0);
        assertThat(result.getTrend()).hasSize(7);
        assertThat(result.getTrend().get(1).getDate()).isEqualTo("2026-07-06");
        assertThat(result.getTrend().get(1).getNewUsers()).isZero();
        assertThat(result.getTrend().get(5).getActiveUsers()).isEqualTo(3);
    }

    @Test
    void Should_ReturnNullGrowthRate_When_PreviousPeriodIsZero() {
        given(sysUserMapper.selectUserBreakdown()).willReturn(new AdminUserCountBreakdownDTO());
        given(sysUserMapper.countCreatedBetween(any(), any())).willReturn(4L, 0L);
        given(loginEventMapper.countDistinctUsersBetween(any(), any())).willReturn(3L, 0L);
        given(sysUserMapper.selectDailyCreatedBetween(any(), any())).willReturn(List.of());
        given(loginEventMapper.selectDailyActiveBetween(any(), any())).willReturn(List.of());

        AdminUserDashboardDTO result = service.getDashboard(30);

        assertThat(result.getNewUsers().getGrowthRate()).isNull();
        assertThat(result.getActiveUsers().getGrowthRate()).isNull();
        assertThat(result.getTrend()).hasSize(30);
    }

    @Test
    void Should_RejectUnsupportedRangeBeforeQueryingDatabase() {
        assertThatThrownBy(() -> service.getDashboard(8))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(20008);
        verify(sysUserMapper, never()).selectUserBreakdown();
    }

    @Test
    void Should_UseShanghaiHalfOpenRange() {
        given(sysUserMapper.selectUserBreakdown()).willReturn(new AdminUserCountBreakdownDTO());
        given(sysUserMapper.selectDailyCreatedBetween(any(), any())).willReturn(List.of());
        given(loginEventMapper.selectDailyActiveBetween(any(), any())).willReturn(List.of());

        service.getDashboard(7);

        verify(sysUserMapper).selectDailyCreatedBetween(
                LocalDateTime.parse("2026-07-05T00:00:00"),
                LocalDateTime.parse("2026-07-12T00:00:00"));
    }

    private UserDailyCountDTO daily(String date, long count) {
        UserDailyCountDTO dto = new UserDailyCountDTO();
        dto.setDate(date);
        dto.setCount(count);
        return dto;
    }
}
