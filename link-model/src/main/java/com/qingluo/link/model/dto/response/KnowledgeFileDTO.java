package com.qingluo.link.model.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 原文件响应 DTO。
 *
 * <p>一期只返回原文件上传事实，供前端展示文件列表、上传状态和失败原因。
 * 不包含解析任务状态、解析进度、解析产物地址，避免前端误以为一期已经具备解析闭环。
 */
@Data
public class KnowledgeFileDTO {

    /** 原文件记录 ID，对应 document_original_file.id。 */
    private Long id;

    /** 文件所属数据集 ID，用于前端回显和后续解析按钮绑定。 */
    private Long datasetId;

    /** 用户上传时的原始文件名，保留大小写，不做业务规范化。 */
    private String originalFilename;

    /** 文件后缀，小写存储；与 originalFilename 一起参与同名文件幂等判断。 */
    private String fileSuffix;

    /** 原文件大小，单位字节。 */
    private Long fileSize;

    /** 业务桶名称，一期原文件固定为 rag-raw。 */
    private String bucketName;

    /** MinIO 对象 key，用于服务端定位私有对象，不直接作为公开访问地址。 */
    private String objectKey;

    /** Java 端内部下载地址，后续 Python 解析端通过服务 Token 读取原文件。 */
    private String fileUrl;

    /** 上传状态：uploading、success、failed。 */
    private String uploadStatus;

    /** 是否已成功写入 MinIO 并完成数据库成功状态回写。 */
    private Boolean isUploadSuccess;

    /** 上传失败原因；成功状态下为空。 */
    private String failureReason;

    /** 原文件记录创建时间。 */
    private LocalDateTime createdAt;

    /** 原文件记录最近更新时间，上传超时补偿依赖该时间判断。 */
    private LocalDateTime updatedAt;
}
