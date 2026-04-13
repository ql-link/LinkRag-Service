package com.qingluo.link.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 对话信息 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {

    private Long id;
    private String title;
    private Long lastConfigId;
    private String lastModelName;
    private Boolean isPinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}