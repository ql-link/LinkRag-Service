package com.qingluo.link.service.delete;

import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 删除通知发送点（占位 / 纯设计预留）。
 *
 * <p>隐性删除（软删保留原文件）后，Java 端不删除解析域产物（{@code document_parse_file} /
 * {@code document_parsed_log}）与 Python 侧 OSS 产物（清洗文件、向量等）；这些“衍生产物”的删除
 * 由 Python 端负责，未来通过 MQ 通知触发（仿 {@code DocumentParseTaskMQ} / {@code tolink.rag.*} 系列）。
 *
 * <p>本类是为删除链路预留的发送点：在删除事务提交后（afterCommit）被调用，载荷携带被软删的
 * {@code original_file_id} 集合。<b>本次仅留痕日志占位</b>，不落地 MQ producer / topic / 消息体，
 * 也不实现 Python 消费端；具体 MQ 契约单独立项。
 */
@Slf4j
@Component
public class DocumentDeleteNotifier {

    /**
     * 通知 Python 删除被软删原文件对应的衍生产物（占位，不实际投递）。
     *
     * @param originalFileIds 被软删的原文件 id 集合（删数据集为级联整批，删单文件为单个）
     * @param datasetId       所属数据集 id
     * @param userId          操作用户 id
     */
    public void notifyAfterDelete(Collection<Long> originalFileIds, Long datasetId, Long userId) {
        // TODO(soft-delete-dataset-file): 后续落地 MQ producer（仿 DocumentParseTaskMQ，tolink.rag.* 系列），
        //  由 Python 消费并删除解析产物 / 清洗文件 / 向量等衍生产物；当前仅留痕，不实际投递。
        log.info("[delete-notify placeholder] reserved Python delete notification: "
            + "userId={}, datasetId={}, originalFileIds={}", userId, datasetId, originalFileIds);
    }
}
