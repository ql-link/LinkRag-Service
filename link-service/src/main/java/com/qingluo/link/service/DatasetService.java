package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.CreateDatasetRequest;
import com.qingluo.link.model.dto.response.DatasetDTO;
import com.qingluo.link.model.dto.response.PageResult;

public interface DatasetService {

    DatasetDTO create(Long userId, CreateDatasetRequest request);

    PageResult<DatasetDTO> list(Long userId, int page, int pageSize);

    DatasetDTO detail(Long userId, Long datasetId);

    void delete(Long userId, Long datasetId);
}
