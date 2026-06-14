package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.response.ModelCapabilityDTO;
import com.qingluo.link.model.dto.response.ModelCapabilityDetailDTO;
import com.qingluo.link.model.dto.response.ProviderModelDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.cache.ProviderCatalogCacheService;
import com.qingluo.link.service.cache.ProviderCatalogSnapshot;
import com.qingluo.link.service.cache.ProviderRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统厂商服务实现。
 *
 * <p>用户侧目录数据来源由 supported_models JSON 切换为 llm_provider_model 正表，
 * 按厂商聚合「模型→能力」对外展示。</p>
 */
@Service
@RequiredArgsConstructor
public class SystemProviderServiceImpl implements SystemProviderService {

    private final SystemProviderMapper systemProviderMapper;
    private final LLMCapabilityService llmCapabilityService;
    private final ProviderModelService providerModelService;
    private final ProviderCatalogCacheService providerCatalogCacheService;

    @Override
    /**
     * 查询所有启用中的系统厂商配置。
     */
    public List<SystemProvider> getActiveProviders() {
        return systemProviderMapper.selectList(
            new LambdaQueryWrapper<SystemProvider>()
                .eq(SystemProvider::getIsActive, true)
                .orderByDesc(SystemProvider::getPriority)
        );
    }

    @Override
    /**
     * 查询用户侧可用厂商与模型，可按能力过滤；过滤后无可选模型的厂商不返回。
     *
     * <p>读路径优先命中厂商目录缓存（全量快照），命中时不查库；未命中才回源：
     * 查启用厂商后用 IN 一次性批量查回全部上架模型（已消除逐厂商 N+1）。
     * capability 过滤与聚合统一在内存完成，因此各 capability 共享同一份缓存。</p>
     */
    public List<ProviderModelDTO> getActiveProviderModels(String capability) {
        // capability 校验留在缓存外：非法 capability 立即抛错，不触发回源、不污染缓存
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        // 命中缓存时 0 次 DB；未命中时按「索引 + 厂商分片」回源：
        // providersLoader 查启用厂商，modelsLoader 按厂商 id 批量查模型（不按 capability 过滤）
        ProviderCatalogSnapshot snapshot = providerCatalogCacheService.getOrLoad(
                this::loadActiveProviderRefs,
                ids -> providerModelService.listActiveModelsByProviderIds(ids, null));
        return assembleProviderModels(snapshot, normalizedCapability);
    }

    /**
     * 回源启用厂商→轻量引用，priority 倒序由 {@link #getActiveProviders()} 保证。
     */
    private List<ProviderRef> loadActiveProviderRefs() {
        return getActiveProviders().stream()
                .map(p -> new ProviderRef(p.getId(), p.getProviderType(), p.getProviderName(), p.getPriority()))
                .toList();
    }

    /**
     * 在内存对快照做 capability 过滤（null 表示不过滤）、按厂商分组聚合，并丢弃无可选模型的厂商。
     * 与原「SQL 按 capability 过滤 + 逐厂商聚合」等价，仅把数据源由数据库换成缓存快照。
     */
    private List<ProviderModelDTO> assembleProviderModels(ProviderCatalogSnapshot snapshot, String normalizedCapability) {
        List<ProviderRef> providers = snapshot.getProviders();
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }
        List<ProviderModel> models = snapshot.getModels() == null ? List.of() : snapshot.getModels();
        Map<Long, List<ProviderModel>> modelsByProvider = models.stream()
                .filter(model -> normalizedCapability == null || normalizedCapability.equals(model.getCapability()))
                .collect(Collectors.groupingBy(ProviderModel::getProviderId));
        return providers.stream()
                .map(provider -> toProviderModelDTO(provider, modelsByProvider.getOrDefault(provider.getId(), List.of())))
                .filter(dto -> !dto.getModels().isEmpty())
                .toList();
    }

    @Override
    /**
     * 按厂商类型查询系统厂商配置。
     */
    public SystemProvider getByProviderType(String providerType) {
        SystemProvider provider = systemProviderMapper.selectOne(
            new LambdaQueryWrapper<SystemProvider>()
                .eq(SystemProvider::getProviderType, providerType)
        );

        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }
        return provider;
    }

    @Override
    /**
     * 按厂商类型查询启用中的系统厂商配置，禁用时抛出异常。
     */
    public SystemProvider getActiveByProviderType(String providerType) {
        SystemProvider provider = getByProviderType(providerType);
        if (!Boolean.TRUE.equals(provider.getIsActive())) {
            throw new BusinessException(ErrorCode.PROVIDER_DISABLED);
        }
        return provider;
    }

    /**
     * 将厂商及其上架模型能力行聚合为用户侧 DTO：同一模型的多条能力归并为能力列表。
     * rows 由上游一次性批量查出并按厂商分好，此方法内不再查库。
     */
    private ProviderModelDTO toProviderModelDTO(ProviderRef provider, List<ProviderModel> rows) {
        Map<String, List<ModelCapabilityDetailDTO>> grouped = new LinkedHashMap<>();
        for (ProviderModel row : rows) {
            // 暴露每个 (模型,能力) 的协议与入口事实值，让前端/管理端看到「这个能力实际怎么调」
            grouped.computeIfAbsent(row.getModelName(), k -> new ArrayList<>())
                    .add(new ModelCapabilityDetailDTO(row.getCapability(), row.getProtocol(), row.getApiBaseUrl()));
        }
        List<ModelCapabilityDTO> models = grouped.entrySet().stream()
                .map(entry -> new ModelCapabilityDTO(entry.getKey(), entry.getValue()))
                .toList();
        return new ProviderModelDTO(provider.getProviderType(), provider.getProviderName(), models);
    }

    /**
     * 归一化并校验能力值，为空时返回 null 表示不过滤。
     */
    private String normalizeCapabilityIfPresent(String capability) {
        if (!StringUtils.hasText(capability)) {
            return null;
        }
        String normalized = capability.toUpperCase(Locale.ROOT);
        llmCapabilityService.validateCapability(normalized);
        return normalized;
    }
}
