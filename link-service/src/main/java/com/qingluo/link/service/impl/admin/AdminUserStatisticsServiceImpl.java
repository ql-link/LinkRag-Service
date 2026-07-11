package com.qingluo.link.service.impl.admin;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.mapper.UserLoginEventMapper;
import com.qingluo.link.model.dto.response.AdminUserCountBreakdownDTO;
import com.qingluo.link.model.dto.response.AdminUserDashboardDTO;
import com.qingluo.link.model.dto.response.AdminUserPeriodMetricDTO;
import com.qingluo.link.model.dto.response.AdminUserTrendPointDTO;
import com.qingluo.link.model.dto.response.UserDailyCountDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.AdminUserStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminUserStatisticsServiceImpl implements AdminUserStatisticsService {
    private static final Set<Integer> SUPPORTED_RANGES = Set.of(7, 30, 90);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final SysUserMapper sysUserMapper;
    private final UserLoginEventMapper userLoginEventMapper;
    private final Clock userStatisticsClock;

    @Autowired
    public AdminUserStatisticsServiceImpl(SysUserMapper sysUserMapper, UserLoginEventMapper userLoginEventMapper) {
        this(sysUserMapper, userLoginEventMapper, Clock.systemUTC());
    }

    public AdminUserStatisticsServiceImpl(
            SysUserMapper sysUserMapper,
            UserLoginEventMapper userLoginEventMapper,
            Clock userStatisticsClock) {
        this.sysUserMapper = sysUserMapper;
        this.userLoginEventMapper = userLoginEventMapper;
        this.userStatisticsClock = userStatisticsClock;
    }

    @Override
    public AdminUserDashboardDTO getDashboard(int days) {
        if (!SUPPORTED_RANGES.contains(days)) {
            throw new BusinessException(ErrorCode.INVALID_USER_STATISTICS_RANGE);
        }

        LocalDate today = LocalDate.now(userStatisticsClock.withZone(BUSINESS_ZONE));
        LocalDate currentStartDate = today.minusDays(days - 1L);
        LocalDate currentEndDate = today.plusDays(1L);
        LocalDate previousStartDate = currentStartDate.minusDays(days);

        LocalDateTime currentStart = currentStartDate.atStartOfDay();
        LocalDateTime currentEnd = currentEndDate.atStartOfDay();
        LocalDateTime previousStart = previousStartDate.atStartOfDay();

        AdminUserCountBreakdownDTO breakdown = sysUserMapper.selectUserBreakdown();
        if (breakdown == null) {
            breakdown = new AdminUserCountBreakdownDTO();
        }

        long currentNewUsers = sysUserMapper.countCreatedBetween(currentStart, currentEnd);
        long previousNewUsers = sysUserMapper.countCreatedBetween(previousStart, currentStart);
        long currentActiveUsers = userLoginEventMapper.countDistinctUsersBetween(currentStart, currentEnd);
        long previousActiveUsers = userLoginEventMapper.countDistinctUsersBetween(previousStart, currentStart);

        List<AdminUserTrendPointDTO> trend = mergeTrend(
                currentStartDate,
                today,
                sysUserMapper.selectDailyCreatedBetween(currentStart, currentEnd),
                userLoginEventMapper.selectDailyActiveBetween(currentStart, currentEnd));

        long totalUsers = breakdown.getUser() + breakdown.getAdmin();
        return new AdminUserDashboardDTO(
                days,
                totalUsers,
                breakdown,
                metric(currentNewUsers, previousNewUsers),
                metric(currentActiveUsers, previousActiveUsers),
                trend);
    }

    private AdminUserPeriodMetricDTO metric(long current, long previous) {
        Double growthRate = previous == 0 ? null : (double) (current - previous) / previous;
        return new AdminUserPeriodMetricDTO(current, previous, growthRate);
    }

    private List<AdminUserTrendPointDTO> mergeTrend(
            LocalDate start,
            LocalDate end,
            List<UserDailyCountDTO> newUserCounts,
            List<UserDailyCountDTO> activeUserCounts) {
        Map<String, Long> newUsersByDate = toCountMap(newUserCounts);
        Map<String, Long> activeUsersByDate = toCountMap(activeUserCounts);
        return start.datesUntil(end.plusDays(1))
                .map(date -> {
                    String key = date.toString();
                    return new AdminUserTrendPointDTO(
                            key,
                            newUsersByDate.getOrDefault(key, 0L),
                            activeUsersByDate.getOrDefault(key, 0L));
                })
                .toList();
    }

    private Map<String, Long> toCountMap(List<UserDailyCountDTO> counts) {
        Map<String, Long> result = new HashMap<>();
        if (counts != null) {
            for (UserDailyCountDTO count : counts) {
                result.put(count.getDate(), count.getCount());
            }
        }
        return result;
    }
}
