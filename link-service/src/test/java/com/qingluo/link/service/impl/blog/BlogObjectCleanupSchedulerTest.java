package com.qingluo.link.service.impl.blog;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class BlogObjectCleanupSchedulerTest {

    @Mock private IOssService ossService;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void committedTransaction_deletesOldObjectAfterCommitOnly() {
        when(ossService.deleteFile(OssSavePlaceEnum.PUBLIC, "old.md")).thenReturn(true);
        BlogObjectCleanupScheduler scheduler = new BlogObjectCleanupScheduler(ossService);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        scheduler.afterCommitDelete(30L, List.of("old.md"));

        verify(ossService, never()).deleteFile(OssSavePlaceEnum.PUBLIC, "old.md");
        List<TransactionSynchronization> synchronizations =
            TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(ossService).deleteFile(OssSavePlaceEnum.PUBLIC, "old.md");
    }

    @Test
    void rolledBackTransaction_keepsOldObject() {
        BlogObjectCleanupScheduler scheduler = new BlogObjectCleanupScheduler(ossService);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        scheduler.afterCommitDelete(30L, List.of("old.md"));

        TransactionSynchronizationManager.getSynchronizations()
            .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(ossService, never()).deleteFile(OssSavePlaceEnum.PUBLIC, "old.md");
    }
}
