package com.qingluo.link.service.delete;

import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.service.mq.DocumentDeleteNotifyMQ;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 删除通知 producer：原文件被隐性删除（软删保留原文件）后，通知 Python 删除其侧衍生产物
 * （{@code document_parse_file} / {@code document_parsed_log} + OSS 清洗文件/向量等）。
 *
 * <p>在删除事务提交后（afterCommit）被调用——回滚不发，避免对未真正删除的数据误通知。按删除范围分流投递
 * {@link DocumentDeleteNotifyMQ}：删数据集发 dataset 范围（仅 dataset_id），删文件发 file 范围（original_file_id）。
 *
 * <p><b>可靠性：尽力发</b>。afterCommit 已脱离事务、无法回滚，发送失败（含 MQ 不可用、发送器缺失）一律
 * <b>告警留痕后吞掉，绝不外抛</b>——否则会污染 {@code TransactionSynchronization} 回调链并可能刷 500。
 * 漏发的代价是衍生产物滞留（惰性垃圾、不影响活记录），已接受；不引入 DLQ / 对账。幂等由 Python 按 id 删
 * 天然保证（删二次为 no-op），故不带去重字段。
 *
 * <p>注意：与 {@code DocumentParseTaskMQ} 的投递（事务内、发送器缺失抛 {@code IllegalStateException}、失败外抛）
 * <b>刻意相反</b>——解析投递在事务内需阻断请求，删除通知在提交后只能尽力而为。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentDeleteNotifier {

    private final ObjectProvider<MQSend> mqSendProvider;

    /**
     * 通知 Python 删除某数据集名下全部衍生产物（删数据集场景）。
     *
     * @param datasetId 被软删的数据集 id
     * @param userId    操作用户 id
     */
    public void notifyDatasetDeleted(Long datasetId, Long userId) {
        send(DocumentDeleteNotifyMQ.forDataset(datasetId, userId));
    }

    /**
     * 通知 Python 删除某原文件的衍生产物（删单文件场景）。
     *
     * @param originalFileId 被软删的原文件 id
     * @param datasetId      所属数据集 id
     * @param userId         操作用户 id
     */
    public void notifyFileDeleted(Long originalFileId, Long datasetId, Long userId) {
        send(DocumentDeleteNotifyMQ.forFile(originalFileId, datasetId, userId));
    }

    private void send(DocumentDeleteNotifyMQ message) {
        try {
            MQSend sender = mqSendProvider.getIfAvailable();
            if (sender == null) {
                // 发送器未装配：尽力发口径下不阻断、不抛，仅告警留痕（接受本条漏发）。
                log.error("[delete-notify] MQ sender unavailable, drop notify: {}", message.getMessage());
                return;
            }
            sender.send(message);
        } catch (RuntimeException e) {
            // afterCommit 已提交、不可回滚；发送失败仅告警留痕并吞掉，不影响已完成的删除。
            log.error("[delete-notify] send failed, drop notify: name={}", message.getMQName(), e);
        }
    }
}
