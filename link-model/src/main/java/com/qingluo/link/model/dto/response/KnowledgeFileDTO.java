package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 原文件响应 DTO。
 *
 * <p>一期只返回原文件上传事实，供前端展示文件列表、上传状态和失败原因。
 * 不包含解析任务状态、解析进度、解析产物地址，避免前端误以为一期已经具备解析闭环。
 */
@Data
@Schema(description = "知识原文件响应")
public class KnowledgeFileDTO {

    /** 原文件记录 ID，对应 document_original_file.id。 */
    @Schema(description = "原文件记录ID，对应 document_original_file.id", example = "10000")
    private Long id;

    /** 文件所属数据集 ID，用于前端回显和后续解析按钮绑定。 */
    @Schema(description = "文件所属数据集ID", example = "10001")
    private Long datasetId;

    /** 用户上传时的原始文件名，保留大小写，不做业务规范化。 */
    @Schema(description = "用户上传时的原始文件名", example = "Guide.MD")
    private String originalFilename;

    /** 文件后缀，小写存储；与 originalFilename 一起参与同名文件幂等判断。 */
    @Schema(description = "文件后缀，小写存储", example = "md")
    private String fileSuffix;

    /** 原文件大小，单位字节。 */
    @Schema(description = "原文件大小，单位字节", example = "102400")
    private Long fileSize;

    /** 业务桶名称，一期原文件固定为 rag-raw；仅服务端内部使用，不暴露给前端。 */
    @Schema(description = "服务端内部桶名称，不对前端序列化", hidden = true)
    private transient String bucketName;

    /** MinIO 对象 key，用于服务端定位私有对象，不直接作为公开访问地址。 */
    @Schema(description = "服务端内部对象Key，不对前端序列化", hidden = true)
    private transient String objectKey;

    /** Java 端内部下载地址，后续 Python 解析端通过服务 Token 读取原文件。 */
    @Schema(description = "服务端内部下载地址，不对前端序列化", hidden = true)
    private transient String fileUrl;

    /** 上传状态：uploading、success、failed。 */
    @Schema(description = "上传状态：uploading、success、failed", example = "success")
    private String uploadStatus;

    /** 是否已成功写入 MinIO 并完成数据库成功状态回写。 */
    @Schema(description = "是否已成功写入对象存储并完成数据库成功状态回写", example = "true")
    private Boolean isUploadSuccess;

    /** 上传失败原因；成功状态下为空。 */
    @Schema(description = "上传失败原因编码，成功状态下为空", example = "OSS_UPLOAD_FAILED")
    private String failureReason;

    /** 原文件记录创建时间。 */
    @Schema(description = "原文件记录创建时间")
    private LocalDateTime createdAt;

    /** 原文件记录最近更新时间，上传超时补偿依赖该时间判断。 */
    @Schema(description = "原文件记录最近更新时间")
    private LocalDateTime updatedAt;
}
