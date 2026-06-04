package com.qingluo.link.service.cache;

import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.mq.model.RagCacheSyncMQ;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 用户 LLM 配置变更后通知 Python RAG 清理配置缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagCacheSyncNotifier {

    private final ObjectProvider<MQSend> mqSendProvider;

    public void notifyRefresh(Long userId, Long configId) {
        notifyAfterCommit(userId, configId, RagCacheSyncMQ.ACTION_REFRESH);
    }

    public void notifyInvalidate(Long userId, Long configId) {
        notifyAfterCommit(userId, configId, RagCacheSyncMQ.ACTION_INVALIDATE);
    }

    public void notifySystemRefresh() {
        notifyAfterCommit(0L, null, RagCacheSyncMQ.ACTION_REFRESH);
    }

    public void notifyAfterCommit(Long userId, Long configId, String action) {
        Runnable task = () -> send(userId, configId, action);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void send(Long userId, Long configId, String action) {
        try {
            MQSend sender = mqSendProvider.getIfAvailable();
            if (sender == null) {
                log.warn("Skip RAG cache sync because MQ sender is unavailable, userId={}, configId={}, action={}",
                        userId, configId, action);
                return;
            }
            sender.send(new RagCacheSyncMQ(new RagCacheSyncMQ.MsgPayload(
                    String.valueOf(userId),
                    configId == null ? null : String.valueOf(configId),
                    action)));
        } catch (RuntimeException ex) {
            log.error("Send RAG cache sync failed, userId={}, configId={}, action={}",
                    userId, configId, action, ex);
        }
    }
}
