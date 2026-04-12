package com.qingluo.link.service.mapper;

import com.qingluo.link.core.dto.response.DailyUsageDTO;
import com.qingluo.link.core.dto.response.UsageLogDTO;
import com.qingluo.link.core.dto.response.UsageSummaryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UsageLogMapper {

    UsageSummaryDTO selectSummaryByUserId(@Param("userId") String userId,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate);

    List<DailyUsageDTO> selectDailyUsageByUserId(@Param("userId") String userId,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate);

    List<UsageLogDTO> selectLogsByUserId(@Param("userId") String userId,
                                          @Param("startDate") String startDate,
                                          @Param("endDate") String endDate);
}