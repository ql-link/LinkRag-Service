package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "数据集信息")
public class DatasetDTO {

    @Schema(description = "数据集ID", example = "10001")
    private Long id;

    @Schema(description = "数据集名称", example = "我的数据集")
    private String name;

    @Schema(description = "数据集描述")
    private String description;

    @Schema(description = "数据集状态", example = "ACTIVE")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
