package com.qingluo.link.service.cache.replay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.CacheReplayEventMapper;
import com.qingluo.link.model.dto.entity.CacheReplayEvent;
import com.qingluo.link.model.dto.response.CacheReplayEventDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.enums.CacheReplayStatus;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.observability.log.AuditLog;
import com.qingluo.link.service.mq.CacheCompensationMQ;
import com.qingluo.link.service.mq.cdc.CdcBridgeService;
import com.qingluo.link.service.mq.cdc.CdcSourceIdentity;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CacheReplayEventService {

    public static final String STAGE_CDC_BRIDGE = "CDC_BRIDGE";
    public static final String STAGE_CACHE_COMPENSATION = "CACHE_COMPENSATION";

    private final CacheReplayEventMapper mapper;
    private final ObjectProvider<CdcBridgeService> cdcBridgeServiceProvider;
    private final ObjectProvider<CacheCompensationMQ.MQReceiver> compensationReceiverProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String stage, String topic, int partition, long offset,
                              String rawPayload, String reason, Throwable error) {
        String eventKey = eventKey(stage, topic, partition, offset);
        CacheReplayEvent existing = findByEventKey(eventKey);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            CacheReplayEvent event = new CacheReplayEvent();
            event.setEventKey(eventKey);
            event.setStage(stage);
            event.setSourceTopic(topic);
            event.setPartitionNo(partition);
            event.setSourceOffset(offset);
            event.setRawPayload(rawPayload);
            event.setFailureReason(reason);
            event.setErrorMessage(errorMessage(error));
            event.setStatus(CacheReplayStatus.PENDING.name());
            event.setFailCount(1);
            event.setFirstFailedAt(now);
            event.setLastFailedAt(now);
            try {
                mapper.insert(event);
                return;
            } catch (DuplicateKeyException ignored) {
                existing = findByEventKey(eventKey);
            }
        }
        CacheReplayEvent update = new CacheReplayEvent();
        update.setId(existing.getId());
        update.setRawPayload(rawPayload);
        update.setFailureReason(reason);
        update.setErrorMessage(errorMessage(error));
        update.setFailCount((existing.getFailCount() == null ? 0 : existing.getFailCount()) + 1);
        update.setLastFailedAt(now);
        if (!CacheReplayStatus.REPLAYED.name().equals(existing.getStatus())
            && !CacheReplayStatus.IGNORED.name().equals(existing.getStatus())) {
            update.setStatus(CacheReplayStatus.PENDING.name());
        }
        mapper.updateById(update);
    }

    public boolean isTerminal(String stage, String topic, int partition, long offset) {
        CacheReplayEvent event = findByEventKey(eventKey(stage, topic, partition, offset));
        return event != null && (CacheReplayStatus.REPLAYED.name().equals(event.getStatus())
            || CacheReplayStatus.IGNORED.name().equals(event.getStatus()));
    }

    public PageResult<CacheReplayEventDTO> list(String status, int page, int size) {
        LambdaQueryWrapper<CacheReplayEvent> query = new LambdaQueryWrapper<CacheReplayEvent>()
            .orderByDesc(CacheReplayEvent::getLastFailedAt)
            .orderByDesc(CacheReplayEvent::getId);
        if (StringUtils.hasText(status)) {
            CacheReplayStatus parsed;
            try {
                parsed = CacheReplayStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(400, "缓存重放状态不合法", 400);
            }
            query.eq(CacheReplayEvent::getStatus, parsed.name());
        }
        Page<CacheReplayEvent> result = mapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(this::toDTO).toList(),
            result.getTotal(), page, size);
    }

    @Transactional
    public CacheReplayEventDTO replay(Long id, Long operatorId) {
        CacheReplayEvent event = requirePending(id);
        if (STAGE_CDC_BRIDGE.equals(event.getStage())) {
            CdcBridgeService bridge = cdcBridgeServiceProvider.getIfAvailable();
            if (bridge == null) {
                throw new BusinessException(ErrorCode.CACHE_REPLAY_EVENT_INVALID_STATE, "CDC bridge 当前不可用");
            }
            int sent = bridge.handle(event.getRawPayload(), new CdcSourceIdentity(
                event.getSourceTopic(), event.getPartitionNo(), event.getSourceOffset()));
            if (sent == 0) {
                throw new BusinessException(ErrorCode.CACHE_REPLAY_EVENT_INVALID_STATE,
                    "重放未生成任何缓存失效目标");
            }
        } else if (STAGE_CACHE_COMPENSATION.equals(event.getStage())) {
            CacheCompensationMQ.MQReceiver receiver = compensationReceiverProvider.getIfAvailable();
            if (receiver == null) {
                throw new BusinessException(ErrorCode.CACHE_REPLAY_EVENT_INVALID_STATE, "缓存补偿消费者当前不可用");
            }
            receiver.receive(CacheCompensationMQ.parseMsg(event.getRawPayload()));
        } else {
            throw new BusinessException(ErrorCode.CACHE_REPLAY_EVENT_INVALID_STATE);
        }
        markTerminal(event, CacheReplayStatus.REPLAYED);
        AuditLog.event("CACHE_REPLAY", "operatorId={}, replayEventId={}, eventKey={}",
            operatorId, id, event.getEventKey());
        return toDTO(mapper.selectById(id));
    }

    @Transactional
    public CacheReplayEventDTO ignore(Long id, Long operatorId) {
        CacheReplayEvent event = requirePending(id);
        markTerminal(event, CacheReplayStatus.IGNORED);
        AuditLog.event("CACHE_REPLAY_IGNORE", "operatorId={}, replayEventId={}, eventKey={}",
            operatorId, id, event.getEventKey());
        return toDTO(mapper.selectById(id));
    }

    private void markTerminal(CacheReplayEvent event, CacheReplayStatus status) {
        CacheReplayEvent update = new CacheReplayEvent();
        update.setId(event.getId());
        update.setStatus(status.name());
        update.setReplayedAt(LocalDateTime.now());
        mapper.updateById(update);
    }

    private CacheReplayEvent requirePending(Long id) {
        CacheReplayEvent event = mapper.selectById(id);
        if (event == null) {
            throw new BusinessException(ErrorCode.CACHE_REPLAY_EVENT_NOT_FOUND);
        }
        if (!CacheReplayStatus.PENDING.name().equals(event.getStatus())) {
            throw new BusinessException(ErrorCode.CACHE_REPLAY_EVENT_INVALID_STATE);
        }
        return event;
    }

    private CacheReplayEvent findByEventKey(String eventKey) {
        return mapper.selectOne(new LambdaQueryWrapper<CacheReplayEvent>()
            .eq(CacheReplayEvent::getEventKey, eventKey));
    }

    private String eventKey(String stage, String topic, int partition, long offset) {
        return stage + ":" + topic + ":" + partition + ":" + offset;
    }

    private String errorMessage(Throwable error) {
        if (error == null || error.getMessage() == null) {
            return null;
        }
        String message = error.getMessage();
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    private CacheReplayEventDTO toDTO(CacheReplayEvent event) {
        CacheReplayEventDTO dto = new CacheReplayEventDTO();
        dto.setId(event.getId());
        dto.setEventKey(event.getEventKey());
        dto.setStage(event.getStage());
        dto.setSourceTopic(event.getSourceTopic());
        dto.setPartitionNo(event.getPartitionNo());
        dto.setSourceOffset(event.getSourceOffset());
        dto.setFailureReason(event.getFailureReason());
        dto.setErrorMessage(event.getErrorMessage());
        dto.setStatus(event.getStatus());
        dto.setFailCount(event.getFailCount());
        dto.setFirstFailedAt(event.getFirstFailedAt());
        dto.setLastFailedAt(event.getLastFailedAt());
        dto.setReplayedAt(event.getReplayedAt());
        return dto;
    }
}
