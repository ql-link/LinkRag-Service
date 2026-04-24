package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建数据集请求")
public class CreateDatasetRequest {

    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 128, message = "数据集名称长度不能超过128")
    @Schema(description = "数据集名称", example = "我的数据集")
    private String name;

    @Size(max = 512, message = "数据集描述长度不能超过512")
    @Schema(description = "数据集描述", example = "用于知识问答")
    private String description;
}
