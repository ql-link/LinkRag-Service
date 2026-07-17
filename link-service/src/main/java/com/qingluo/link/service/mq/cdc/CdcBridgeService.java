package com.qingluo.link.service.mq.cdc;

import com.alibaba.fastjson.JSON;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.observability.trace.TraceContext;
import com.qingluo.link.service.mq.CacheCompensationMQ;
import com.qingluo.link.service.mq.cdc.CdcCacheEvictMapping.ChangeRow;
import com.qingluo.link.service.mq.cdc.CdcCacheEvictMapping.MappingRule;
import com.qingluo.link.service.mq.cdc.CdcCacheEvictMapping.RouteResolution;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdcBridgeService {

    private static final List<String> DML_TYPES = List.of("INSERT", "UPDATE", "DELETE");

    private final ObjectProvider<MQSend> mqSendProvider;
    private final CdcCacheEvictMapping mapping;
    private final CacheConsistencyProperties properties;

    public int handle(String rawJson) {
        long stableOffset = Integer.toUnsignedLong(Objects.requireNonNullElse(rawJson, "").hashCode());
        return handle(rawJson, new CdcSourceIdentity("direct", 0, stableOffset));
    }

    public int handle(String rawJson, CdcSourceIdentity source) {
        CanalChangeEvent event = parse(rawJson);
        if (event.isDdl()) {
            return 0;
        }
        if (!StringUtils.hasText(event.getTable()) || !StringUtils.hasText(event.getType())) {
            throw new CdcEventException("BAD_PAYLOAD", "canal event missing table/type");
        }
        List<MappingRule> rules = mapping.rulesOf(event.getTable());
        if (rules.isEmpty() || !properties.getCdc().isMappingsEnabled()) {
            return 0;
        }
        if (!StringUtils.hasText(event.getDatabase())) {
            throw new CdcEventException("BAD_PAYLOAD", "mapped canal event database is missing");
        }
        if (!event.getDatabase().equalsIgnoreCase(properties.getCdc().getDatabase())) {
            return 0;
        }
        String operation = event.getType().trim().toUpperCase(Locale.ROOT);
        if (!DML_TYPES.contains(operation)) {
            throw new CdcEventException("OPERATION_UNKNOWN", "unsupported canal operation: " + operation);
        }
        if (CollectionUtils.isEmpty(event.getData())) {
            throw new CdcEventException("ROW_ARRAY_EMPTY", "mapped canal event data is empty");
        }
        return expandAndSend(event, operation, rules, source);
    }

    private CanalChangeEvent parse(String rawJson) {
        try {
            CanalChangeEvent event = JSON.parseObject(rawJson, CanalChangeEvent.class);
            if (event == null) {
                throw new CdcEventException("BAD_PAYLOAD", "canal event is null");
            }
            return event;
        } catch (CdcEventException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new CdcEventException("BAD_PAYLOAD", "canal event parse failed: " + ex.getMessage(), ex);
        }
    }

    private int expandAndSend(CanalChangeEvent event, String operation,
                              List<MappingRule> rules, CdcSourceIdentity source) {
        Map<TargetRoute, Integer> uniqueTargets = new LinkedHashMap<>();
        for (int rowIndex = 0; rowIndex < event.getData().size(); rowIndex++) {
            Map<String, String> current = safe(event.getData().get(rowIndex));
            Map<String, String> before = event.getOld() != null && rowIndex < event.getOld().size()
                ? safe(event.getOld().get(rowIndex))
                : Map.of();
            ChangeRow row = new ChangeRow(operation, current, before);
            for (MappingRule rule : rules) {
                RouteResolution resolution = rule.resolver().resolve(row);
                if (resolution.ignored()) {
                    continue;
                }
                if (resolution.routeIds() == null || resolution.routeIds().isEmpty()) {
                    throw new CdcEventException("ROUTE_MISSING",
                        "route_id missing table=" + event.getTable() + ", target=" + rule.target().getCode());
                }
                for (String routeId : resolution.routeIds()) {
                    if (!StringUtils.hasText(routeId)) {
                        throw new CdcEventException("ROUTE_MISSING",
                            "route_id blank table=" + event.getTable());
                    }
                    uniqueTargets.putIfAbsent(new TargetRoute(rule.target().getCode(), routeId), rowIndex);
                }
            }
        }

        if (uniqueTargets.isEmpty()) {
            return 0;
        }
        MQSend sender = mqSendProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("MQ sender is not configured");
        }
        for (Map.Entry<TargetRoute, Integer> entry : uniqueTargets.entrySet()) {
            TargetRoute target = entry.getKey();
            sender.sendConfirmed(new CacheCompensationMQ(buildPayload(
                event, source, entry.getValue(), target.target(), target.routeId())));
        }
        return uniqueTargets.size();
    }

    private CacheCompensationMQ.MsgPayload buildPayload(CanalChangeEvent event, CdcSourceIdentity source,
                                                         int rowIndex, String target, String routeId) {
        CacheCompensationMQ.MsgPayload payload = new CacheCompensationMQ.MsgPayload();
        payload.setEventId(source.topic() + ":" + source.partition() + ":" + source.offset()
            + ":" + rowIndex + ":" + target + ":" + routeId);
        payload.setCacheTarget(target);
        payload.setRouteId(routeId);
        payload.setSourceTable(event.getTable());
        payload.setOperationType(event.getType().toUpperCase(Locale.ROOT));
        payload.setTraceId(MDC.get(TraceContext.TRACE_ID_KEY));
        payload.setOccurredAt(event.getEs() != null
            ? String.valueOf(event.getEs())
            : String.valueOf(System.currentTimeMillis()));
        return payload;
    }

    private Map<String, String> safe(Map<String, String> value) {
        return value == null ? Map.of() : value;
    }

    private record TargetRoute(String target, String routeId) {
    }
}
