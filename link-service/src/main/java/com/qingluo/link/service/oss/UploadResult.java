package com.qingluo.link.service.oss;

/**
 * 同时携带生成的对象 key 与上传 preview 值：需要存 key 的调用方（如反馈，方案甲）取 objectKey，
 * 需要 URL 的调用方取 previewUrl。
 *
 * <p>previewUrl 与 {@code upload2PreviewUrl} 语义一致：公开桶上传为完整公开 URL，私有桶上传为对象 key 本身。</p>
 */
public record UploadResult(String objectKey, String previewUrl) {
}
