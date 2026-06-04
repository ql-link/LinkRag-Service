package com.qingluo.link.service.impl.llm;

import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.FetchProviderModelsRequest;
import com.qingluo.link.model.dto.response.ProviderModelListDTO;

/**
 * 上游模型列表临时拉取服务。
 */
public interface ProviderModelFetchService {

    ProviderModelListDTO fetchModels(SystemProvider provider, FetchProviderModelsRequest request);
}
