package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.DailyUsageDTO;
import com.qingluo.link.model.dto.response.ModelUsageDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.UsageLogDTO;
import com.qingluo.link.model.dto.response.UsageSummaryDTO;
import com.qingluo.link.model.dto.response.UsageTrendDTO;
import java.util.List;

/**
 * 用量查询服务接口
 *
 * <p>{@code llm_usage_log} 自 LINK-184 起为全链路账本，含 chat（对话生成）与 usage_report
 * 通道写入的 parse/recall 系统侧调用。各查询的 {@code stage} 入参用于按阶段过滤：缺省（null/空）
 * 仅统计对话 {@code chat}，保持原口径；传 {@code all} 统计全链路；传具体阶段名则只统计该阶段。</p>
 */
public interface UsageQueryService {

    /**
     * 获取用量汇总
     *
     * @param stage 阶段过滤；null/空 → 仅 chat，{@code all} → 全链路，其余 → 指定阶段
     */
    UsageSummaryDTO getSummary(Long userId, String startDate, String endDate, String stage);

    /**
     * 获取日度用量
     *
     * @param stage 阶段过滤；null/空 → 仅 chat，{@code all} → 全链路，其余 → 指定阶段
     */
    List<DailyUsageDTO> getDailyUsage(Long userId, String startDate, String endDate, String stage);

    /**
     * 获取用量明细
     *
     * @param stage 阶段过滤；null/空 → 仅 chat，{@code all} → 全链路，其余 → 指定阶段
     */
    PageResult<UsageLogDTO> getUsageLogs(Long userId, String startDate, String endDate, String stage, int page, int pageSize);

    /**
     * 按「厂商 + 模型」聚合用量（全链路口径，不按 stage 过滤），按总 Token 降序。
     */
    List<ModelUsageDTO> getUsageByModel(Long userId, String startDate, String endDate);

    /**
     * 当前周期 vs 等长上一周期的用量环比趋势（全链路口径，不按 stage 过滤）。
     */
    UsageTrendDTO getUsageTrend(Long userId, String startDate, String endDate);
}