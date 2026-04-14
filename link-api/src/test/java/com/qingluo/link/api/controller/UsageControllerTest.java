package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.response.DailyUsageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UsageLogDTO;
import com.qingluo.link.model.dto.response.UsageSummaryDTO;
import com.qingluo.link.service.UsageQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UsageController 控制器测试
 * TDD Red 阶段
 */
@ExtendWith(MockitoExtension.class)
class UsageControllerTest {

    @Mock
    private UsageQueryService usageQueryService;

    @InjectMocks
    private UsageController usageController;

    @Test
    void Should_ReturnUsageSummary_When_GetSummary() {
        // given
        Long userId = 1L;
        UsageSummaryDTO summary = new UsageSummaryDTO(100L, 50000L, 30000L, 20000L, 1500.0);

        when(usageQueryService.getSummary(eq(userId), eq("2026-04-01"), eq("2026-04-14")))
            .thenReturn(summary);

        // when
        Result<UsageSummaryDTO> result = usageController.getSummary("2026-04-01", "2026-04-14");

        // then
        assertNotNull(result);
        assertEquals(100L, result.getData().getTotalCalls());
        assertEquals(50000L, result.getData().getTotalTokens());
        verify(usageQueryService).getSummary(eq(userId), eq("2026-04-01"), eq("2026-04-14"));
    }

    @Test
    void Should_ReturnDailyUsageList_When_GetDailyUsage() {
        // given
        Long userId = 1L;
        DailyUsageDTO daily = new DailyUsageDTO("2026-04-01", 50L, 15000L, 10000L, 25000L);

        when(usageQueryService.getDailyUsage(eq(userId), eq("2026-04-01"), eq("2026-04-14")))
            .thenReturn(List.of(daily));

        // when
        Result<List<DailyUsageDTO>> result = usageController.getDailyUsage("2026-04-01", "2026-04-14");

        // then
        assertNotNull(result);
        assertEquals(1, result.getData().size());
        assertEquals("2026-04-01", result.getData().get(0).getDate());
        verify(usageQueryService).getDailyUsage(eq(userId), eq("2026-04-01"), eq("2026-04-14"));
    }

    @Test
    void Should_ReturnUsageLogs_When_GetUsageLogs() {
        // given
        Long userId = 1L;
        UsageLogDTO log = new UsageLogDTO();
        log.setId(1L);
        log.setModelName("gpt-4");
        log.setTotalTokens(170);

        PageResult<UsageLogDTO> pageResult = new PageResult<>(List.of(log), 1, 1, 20);

        when(usageQueryService.getUsageLogs(eq(userId), eq("2026-04-01"), eq("2026-04-14"), eq(1), eq(20)))
            .thenReturn(pageResult);

        // when
        Result<PageResult<UsageLogDTO>> result = usageController.getUsageLogs("2026-04-01", "2026-04-14", 1, 20);

        // then
        assertNotNull(result);
        assertEquals(1, result.getData().getItems().size());
        assertEquals("gpt-4", result.getData().getItems().get(0).getModelName());
        verify(usageQueryService).getUsageLogs(eq(userId), eq("2026-04-01"), eq("2026-04-14"), eq(1), eq(20));
    }
}
