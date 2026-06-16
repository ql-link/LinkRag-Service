package com.qingluo.link.service.mq.cdc;

import com.alibaba.fastjson.JSON;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.core.trace.TraceContext;
import com.qingluo.link.service.mq.CacheCompensationMQ;
import com.qingluo.link.service.mq.cdc.CdcCacheEvictMapping.MappingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CDC 桥接核心：把一条 Canal 行变更按统一映射展开成若干删缓存消息，投递到 tolink.cache.evict。
 *
 * <p>只翻译 + 投递，不直接删缓存——保持 tolink.cache.evict 为唯一补偿入口，复用既有删缓存与重试语义。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CdcBridgeService {

    private final ObjectProvider<MQSend> mqSendProvider;
    private final CdcCacheEvictMapping mapping;

    /**
     * 处理一条 Canal 原始消息。
     *
     * <p>坏消息（反序列化失败 / 缺必填字段）抛 {@link IllegalArgumentException}，由专用错误处理器判
     * 不可重试、立即跳过告警；发送失败抛出交退避重试。映射未命中正常返回（ack）。</p>
     */
    public void handle(String rawJson) {
        CanalChangeEvent event = parse(rawJson);
        if (event.isDdl()) {
            return;
        }
        if (!StringUtils.hasText(event.getTable()) || !StringUtils.hasText(event.getType())) {
            throw new IllegalArgumentException("canal event missing table/type: " + rawJson);
        }
        if (CollectionUtils.isEmpty(event.getData())) {
            return;
        }
        List<MappingRule> rules = mapping.rulesOf(event.getTable());
        if (rules.isEmpty()) {
            log.debug("No cache-evict mapping for table={}, skip", event.getTable());
            return;
        }
        expandAndSend(event, rules);
    }

    private CanalChangeEvent parse(String rawJson) {
        try {
            CanalChangeEvent event = JSON.parseObject(rawJson, CanalChangeEvent.class);
            if (event == null) {
                throw new IllegalArgumentException("canal event is null: " + rawJson);
            }
            return event;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // fastjson 解析异常归一化为 IllegalArgumentException，命中错误处理器的不可重试分类
            throw new IllegalArgumentException("canal event parse failed: " + ex.getMessage(), ex);
        }
    }

    private void expandAndSend(CanalChangeEvent event, List<MappingRule> rules) {
        MQSend sender = mqSendProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("MQ sender is not configured");
        }
        for (Map<String, String> row : event.getData()) {
            for (MappingRule rule : rules) {
                String routeId = rule.resolver().resolve(row);
                if (!StringUtils.hasText(routeId)) {
                    // 解析取法降级（索引未命中 / 厂商已停用删除）：跳过该条，不连累同消息其他 rule/行
                    log.warn("route_id resolve failed, skip. table={}, target={}",
                            event.getTable(), rule.target().getCode());
                    continue;
                }
                sender.send(new CacheCompensationMQ(buildPayload(event, rule.target().getCode(), routeId)));
            }
        }
    }

    private CacheCompensationMQ.MsgPayload buildPayload(CanalChangeEvent event, String target, String routeId) {
        CacheCompensationMQ.MsgPayload payload = new CacheCompensationMQ.MsgPayload();
        payload.setEventId(UUID.randomUUID().toString());
        payload.setCacheTarget(target);
        payload.setRouteId(routeId);
        payload.setSourceTable(event.getTable());
        payload.setOperationType(event.getType());
        payload.setTraceId(MDC.get(TraceContext.TRACE_ID_KEY));
        payload.setOccurredAt(event.getEs() != null
                ? String.valueOf(event.getEs())
                : String.valueOf(System.currentTimeMillis()));
        return payload;
    }
}
