package com.qingluo.link.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 消息信息 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private Long configId;
    private String modelName;
    private Integer tokenCount;
    private LocalDateTime createdAt;
}