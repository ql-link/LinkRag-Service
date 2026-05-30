package com.qingluo.link.service.delete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class DocumentDeleteNotifierTest {

    @Test
    void Should_SendFileScopeMessage_When_NotifyFileDeleted() {
        MQSend sender = mock(MQSend.class);
        DocumentDeleteNotifier notifier = new DocumentDeleteNotifier(providerOf(sender));

        notifier.notifyFileDeleted(1L, 200L, 100L);

        ArgumentCaptor<AbstractMQ> captor = ArgumentCaptor.forClass(AbstractMQ.class);
        verify(sender).send(captor.capture());
        AbstractMQ msg = captor.getValue();
        assertThat(msg.getMQName()).isEqualTo("tolink.rag.document_delete");
        JSONObject json = JSON.parseObject(msg.getMessage());
        assertThat(json.getString("delete_type")).isEqualTo("file");
        assertThat(json.getLong("original_file_id")).isEqualTo(1L);
        assertThat(json.getLong("dataset_id")).isEqualTo(200L);
        assertThat(json.getLong("user_id")).isEqualTo(100L);
    }

    @Test
    void Should_SendDatasetScopeMessage_WithoutOriginalFileId_When_NotifyDatasetDeleted() {
        MQSend sender = mock(MQSend.class);
        DocumentDeleteNotifier notifier = new DocumentDeleteNotifier(providerOf(sender));

        notifier.notifyDatasetDeleted(10L, 100L);

        ArgumentCaptor<AbstractMQ> captor = ArgumentCaptor.forClass(AbstractMQ.class);
        verify(sender).send(captor.capture());
        JSONObject json = JSON.parseObject(captor.getValue().getMessage());
        assertThat(json.getString("delete_type")).isEqualTo("dataset");
        assertThat(json.getLong("dataset_id")).isEqualTo(10L);
        assertThat(json.getLong("user_id")).isEqualTo(100L);
        assertThat(json.containsKey("original_file_id")).isFalse();
    }

    @Test
    void Should_SwallowAndNotThrow_When_SendFails() {
        MQSend sender = mock(MQSend.class);
        doThrow(new RuntimeException("broker down")).when(sender).send(any(AbstractMQ.class));
        DocumentDeleteNotifier notifier = new DocumentDeleteNotifier(providerOf(sender));

        // afterCommit 已提交、不可回滚：发送失败必须吞掉，不外抛（否则污染同步回调链 / 刷 500）
        assertThatCode(() -> notifier.notifyFileDeleted(1L, 200L, 100L)).doesNotThrowAnyException();
    }

    @Test
    void Should_SwallowAndNotThrow_When_SenderUnavailable() {
        ObjectProvider<MQSend> provider = providerOf(null);
        DocumentDeleteNotifier notifier = new DocumentDeleteNotifier(provider);

        // 发送器未装配（getIfAvailable()==null）：best-effort 下不抛、不 NPE
        assertThatCode(() -> notifier.notifyDatasetDeleted(10L, 100L)).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MQSend> providerOf(MQSend sender) {
        ObjectProvider<MQSend> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }
}
