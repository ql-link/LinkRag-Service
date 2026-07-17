package com.qingluo.link.service.impl.blog;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import java.util.Collection;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 旧博客对象只能在数据库新指针提交后删除。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogObjectCleanupScheduler {

    private final IOssService ossService;

    public void afterCommitDelete(Long postId, Collection<String> objectKeys) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (objectKeys != null) {
            objectKeys.stream().filter(StringUtils::hasText).forEach(keys::add);
        }
        if (keys.isEmpty()) {
            return;
        }
        Runnable cleanup = () -> keys.forEach(key -> deleteQuietly(postId, key));
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    public void afterCommitDelete(Long postId, String objectKey) {
        afterCommitDelete(postId, java.util.List.of(objectKey == null ? "" : objectKey));
    }

    private void deleteQuietly(Long postId, String key) {
        try {
            if (!ossService.deleteFile(OssSavePlaceEnum.PUBLIC, key)) {
                log.warn("Failed to delete old blog object, postId={}, key={}", postId, key);
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to delete old blog object, postId={}, key={}, reason={}",
                postId, key, ex.getMessage());
        }
    }
}
