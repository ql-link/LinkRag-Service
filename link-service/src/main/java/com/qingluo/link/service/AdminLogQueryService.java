package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.LogEntryDTO;
import com.qingluo.link.model.dto.response.LogLabelsDTO;
import com.qingluo.link.model.dto.response.PageResult;

/**
 * 管理端日志查询代理。
 */
public interface AdminLogQueryService {

    PageResult<LogEntryDTO> queryLogs(String service,
                                      String level,
                                      String traceId,
                                      String keyword,
                                      String startTime,
                                      String endTime,
                                      int page,
                                      int pageSize);

    LogLabelsDTO listLabels();
}
