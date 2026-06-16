package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.UpdateDatasetParseConfigRequest;
import com.qingluo.link.model.dto.response.DatasetParseConfigResponse;

/**
 * 数据集级解析/检索配置的纯管理服务：存/改/回显。消费读取与默认值兜底归 Python。
 */
public interface DatasetParseConfigService {

    /**
     * 回显数据集已存配置；无配置行返回四类空对象（未配置），不落库。
     */
    DatasetParseConfigResponse getConfig(Long userId, Long datasetId);

    /**
     * 全量更新（PUT）：整行四类按请求覆盖，无行则建行。原样存储、不补字段默认。
     */
    DatasetParseConfigResponse updateConfig(Long userId, Long datasetId, UpdateDatasetParseConfigRequest request);
}
