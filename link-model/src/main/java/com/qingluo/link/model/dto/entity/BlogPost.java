package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("blog_post")
public class BlogPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String slug;

    private String summary;

    @TableField("content_object_key")
    private String contentObjectKey;

    @TableField("cover_asset_id")
    private Long coverAssetId;

    private String status;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_by")
    private Long createdBy;

    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted = false;

    @TableField("deleted_seq")
    private Long deletedSeq = 0L;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
