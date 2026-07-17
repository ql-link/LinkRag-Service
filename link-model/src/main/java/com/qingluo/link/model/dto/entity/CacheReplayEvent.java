package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("cache_replay_event")
public class CacheReplayEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("event_key")
    private String eventKey;
    private String stage;
    @TableField("source_topic")
    private String sourceTopic;
    @TableField("partition_no")
    private Integer partitionNo;
    @TableField("source_offset")
    private Long sourceOffset;
    @TableField("raw_payload")
    private String rawPayload;
    @TableField("failure_reason")
    private String failureReason;
    @TableField("error_message")
    private String errorMessage;
    private String status;
    @TableField("fail_count")
    private Integer failCount;
    @TableField("first_failed_at")
    private LocalDateTime firstFailedAt;
    @TableField("last_failed_at")
    private LocalDateTime lastFailedAt;
    @TableField("replayed_at")
    private LocalDateTime replayedAt;
}
