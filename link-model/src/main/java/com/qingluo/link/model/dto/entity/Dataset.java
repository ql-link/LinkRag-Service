package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("dataset")
public class Dataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String name;

    private String description;

    private String status;

    /** 逻辑删除标记：软删保留原数据集（隐性删除）；@TableLogic 让读查询自动过滤、delete 转 update，不物理删行。 */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted = false;

    /** 删除判别列：活行恒为 0、软删时置为自身 id；纳入唯一键使死行退出“活名额”，支持删后同名重建。 */
    @TableField("deleted_seq")
    private Long deletedSeq = 0L;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
