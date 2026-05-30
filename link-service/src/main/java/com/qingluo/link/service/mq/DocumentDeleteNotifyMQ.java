package com.qingluo.link.service.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQSendType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Java 向 Python 投递的删除通知消息：原文件被隐性删除（软删）后，由 Python 按本消息删除其侧衍生产物
 * （{@code document_parse_file} / {@code document_parsed_log} 行 + OSS 清洗文件/Markdown/向量）。
 *
 * <p>按删除范围分流（{@code delete_type}）：
 * <ul>
 *   <li>{@code dataset}：携带 {@code dataset_id}，Python 按数据集删名下全部衍生产物（不下发文件 id 列表，
 *       超大数据集消息体仍恒定）；</li>
 *   <li>{@code file}：携带 {@code original_file_id}，Python 按该文件删衍生产物。</li>
 * </ul>
 *
 * <p>幂等天然成立（按 id 删，删二次为 no-op），故不带去重/追踪字段，载荷保持最简。扁平 JSON + snake_case，
 * 仿 {@link DocumentParseTaskMQ}。{@code dataset} 范围下 {@code original_file_id} 为 null，fastjson 默认
 * 跳过 null 字段，序列化结果不含该键。
 */
public class DocumentDeleteNotifyMQ implements AbstractMQ {

    public static final String MQ_NAME = "tolink.rag.document_delete";

    public static final String DELETE_TYPE_DATASET = "dataset";
    public static final String DELETE_TYPE_FILE = "file";

    private MsgPayload msgPayload;

    public DocumentDeleteNotifyMQ() {
        this.msgPayload = new MsgPayload();
    }

    public DocumentDeleteNotifyMQ(MsgPayload msgPayload) {
        this.msgPayload = msgPayload;
    }

    /** 数据集范围：Python 按 dataset_id 删名下全部衍生产物。 */
    public static DocumentDeleteNotifyMQ forDataset(Long datasetId, Long userId) {
        MsgPayload payload = new MsgPayload();
        payload.setDeleteType(DELETE_TYPE_DATASET);
        payload.setDatasetId(datasetId);
        payload.setUserId(userId);
        return new DocumentDeleteNotifyMQ(payload);
    }

    /** 文件范围：Python 按 original_file_id 删该文件衍生产物。 */
    public static DocumentDeleteNotifyMQ forFile(Long originalFileId, Long datasetId, Long userId) {
        MsgPayload payload = new MsgPayload();
        payload.setDeleteType(DELETE_TYPE_FILE);
        payload.setOriginalFileId(originalFileId);
        payload.setDatasetId(datasetId);
        payload.setUserId(userId);
        return new DocumentDeleteNotifyMQ(payload);
    }

    @Override
    public String getMQName() {
        return MQ_NAME;
    }

    @Override
    public MQSendType getMQType() {
        return MQSendType.QUEUE;
    }

    @Override
    public String getMessage() {
        validate(msgPayload);
        return JSON.toJSONString(msgPayload);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgPayload {
        @JSONField(name = "delete_type")
        private String deleteType;
        @JSONField(name = "dataset_id")
        private Long datasetId;
        @JSONField(name = "user_id")
        private Long userId;
        // 仅 delete_type=file 时填充；dataset 范围为 null，序列化时被 fastjson 省略。
        @JSONField(name = "original_file_id")
        private Long originalFileId;
    }

    private static void validate(MsgPayload payload) {
        if (payload == null
            || (!DELETE_TYPE_DATASET.equals(payload.getDeleteType())
                && !DELETE_TYPE_FILE.equals(payload.getDeleteType()))) {
            throw new IllegalArgumentException("document_delete delete_type is invalid");
        }
        if (payload.getDatasetId() == null || payload.getUserId() == null) {
            throw new IllegalArgumentException("document_delete ownership is missing");
        }
        // file 范围必须定位到具体原文件；dataset 范围由 dataset_id 定位、不需要 original_file_id。
        if (DELETE_TYPE_FILE.equals(payload.getDeleteType()) && payload.getOriginalFileId() == null) {
            throw new IllegalArgumentException("document_delete original_file_id is missing for file");
        }
    }
}
