package com.qingluo.link.service.mq.cdc;

import com.alibaba.fastjson.JSON;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.service.cache.ProviderCatalogCacheService;
import com.qingluo.link.service.mq.CacheCompensationMQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link CdcBridgeService} 单元测试。
 *
 * <p>覆盖 acceptance：统一映射展开（单目标直取 / 一表多目标 / 解析取法 / 增删改）、消息字段完整、
 * 解析失败降级、未配置表忽略、非缓存字段仍投递、坏消息抛出、发送失败冒泡。</p>
 */
@ExtendWith(MockitoExtension.class)
class CdcBridgeServiceTest {

    @Mock
    private org.springframework.beans.factory.ObjectProvider<MQSend> mqSendProvider;

    @Mock
    private MQSend mqSend;

    @Mock
    private ProviderCatalogCacheService providerCatalog;

    private CdcBridgeService service;

    @BeforeEach
    void setUp() {
        CdcCacheEvictMapping mapping = new CdcCacheEvictMapping(providerCatalog);
        service = new CdcBridgeService(mqSendProvider, mapping);
        lenient().when(mqSendProvider.getIfAvailable()).thenReturn(mqSend);
    }

    @Test
    @DisplayName("单目标直取表 sys_user：展开 1 条 USER route_id=id")
    void singleTarget_sysUser() {
        service.handle(canal("sys_user", "UPDATE", row("id", "100")));

        List<CacheCompensationMQ.MsgPayload> sent = capture(1);
        assertThat(sent).singleElement().satisfies(p -> {
            assertThat(p.getCacheTarget()).isEqualTo("user");
            assertThat(p.getRouteId()).isEqualTo("100");
        });
    }

    @Test
    @DisplayName("单目标直取表 llm_system_provider：route_id 取 provider_type")
    void singleTarget_systemProvider() {
        service.handle(canal("llm_system_provider", "UPDATE", row("provider_type", "openai")));

        assertThat(capture(1)).singleElement().satisfies(p -> {
            assertThat(p.getCacheTarget()).isEqualTo("system_provider");
            assertThat(p.getRouteId()).isEqualTo("openai");
        });
    }

    @Test
    @DisplayName("一表多目标 llm_user_config：展开 2 条（LLM_CONFIG=id, USER_DEFAULT_LLM_CONFIG=user_id）")
    void twoTargets_userConfig() {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("id", "8");
        r.put("user_id", "100");
        service.handle(canal("llm_user_config", "UPDATE", r));

        List<CacheCompensationMQ.MsgPayload> sent = capture(2);
        assertThat(sent).extracting(CacheCompensationMQ.MsgPayload::getCacheTarget)
                .containsExactlyInAnyOrder("llm_config", "user_default_llm_config");
        assertThat(sent).extracting(CacheCompensationMQ.MsgPayload::getRouteId)
                .containsExactlyInAnyOrder("8", "100");
    }

    @Test
    @DisplayName("解析取法 llm_provider_model：查索引缓存换 provider_type → SYSTEM_PROVIDER")
    void resolveTakes_providerModel() {
        given(providerCatalog.resolveProviderTypeById(3L)).willReturn("openai");

        service.handle(canal("llm_provider_model", "INSERT", row("provider_id", "3")));

        assertThat(capture(1)).singleElement().satisfies(p -> {
            assertThat(p.getCacheTarget()).isEqualTo("system_provider");
            assertThat(p.getRouteId()).isEqualTo("openai");
        });
    }

    @Test
    @DisplayName("增/删/改三种操作都触发投递")
    void allOperations() {
        for (String op : List.of("INSERT", "UPDATE", "DELETE")) {
            service.handle(canal("sys_user", op, row("id", "100")));
        }
        ArgumentCaptor<CacheCompensationMQ> captor = ArgumentCaptor.forClass(CacheCompensationMQ.class);
        verify(mqSend, times(3)).send(captor.capture());
        assertThat(captor.getAllValues()).hasSize(3);
    }

    @Test
    @DisplayName("消息字段完整：event_id/source_table/operation_type/occurred_at/trace_id 齐全且过 validate")
    void payloadFieldsComplete() {
        // 模拟 Receiver 已 startNew 的 MDC traceId，验证 buildPayload 正确取用，锁住 acceptance「trace_id 非空」
        org.slf4j.MDC.put(com.qingluo.link.core.trace.TraceContext.TRACE_ID_KEY, "trace-xyz");
        try {
            service.handle(canal("sys_user", "UPDATE", row("id", "100")));

            // capture 内部用 parseMsg 反序列化，等价于跑了一遍 validate（缺必填会抛）
            CacheCompensationMQ.MsgPayload p = capture(1).get(0);
            assertThat(p.getEventId()).isNotBlank();
            assertThat(p.getSourceTable()).isEqualTo("sys_user");
            assertThat(p.getOperationType()).isEqualTo("UPDATE");
            assertThat(p.getOccurredAt()).isNotBlank();
            assertThat(p.getTraceId()).isEqualTo("trace-xyz");
        } finally {
            org.slf4j.MDC.remove(com.qingluo.link.core.trace.TraceContext.TRACE_ID_KEY);
        }
    }

    @Test
    @DisplayName("解析失败降级：索引拿不到 provider_type → 跳过该条，不投递、不抛")
    void degrade_whenResolveReturnsNull() {
        given(providerCatalog.resolveProviderTypeById(3L)).willReturn(null);

        service.handle(canal("llm_provider_model", "DELETE", row("provider_id", "3")));

        verify(mqSend, never()).send(any());
    }

    @Test
    @DisplayName("未配置映射的表：忽略，不投递、不抛")
    void ignore_unmappedTable() {
        service.handle(canal("llm_system_preset", "UPDATE", row("id", "1")));

        verify(mqSend, never()).send(any());
    }

    @Test
    @DisplayName("一条 Canal 消息含多行 data：逐行展开（sys_user 两行 → 2 条）")
    void multipleRows_expandedPerRow() {
        service.handle(canal("sys_user", "UPDATE", row("id", "100"), row("id", "200")));

        assertThat(capture(2)).extracting(CacheCompensationMQ.MsgPayload::getRouteId)
                .containsExactlyInAnyOrder("100", "200");
    }

    @Test
    @DisplayName("非缓存相关字段变更仍照常投递（不读 old、宁多勿漏）")
    void nonCacheColumnChange_stillSends() {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("id", "100");
        r.put("last_login_at", "2026-06-10T00:00:00");
        service.handle(canal("sys_user", "UPDATE", r));

        assertThat(capture(1)).singleElement()
                .satisfies(p -> assertThat(p.getRouteId()).isEqualTo("100"));
    }

    @Test
    @DisplayName("坏消息：非法 JSON → 抛 IllegalArgumentException（错误处理器判不可重试）")
    void badMessage_illegalJson() {
        assertThatThrownBy(() -> service.handle("not-a-json"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mqSend, never()).send(any());
    }

    @Test
    @DisplayName("坏消息：缺 table → 抛 IllegalArgumentException")
    void badMessage_missingTable() {
        String json = JSON.toJSONString(Map.of("type", "UPDATE", "data", List.of(row("id", "100"))));
        assertThatThrownBy(() -> service.handle(json))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("坏消息：被监听表直取字段缺失（sys_user 无 id）→ 抛 IllegalArgumentException")
    void badMessage_missingDirectField() {
        assertThatThrownBy(() -> service.handle(canal("sys_user", "UPDATE", row("name", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("发送失败：MQSend 抛异常 → 冒泡（交错误处理器退避重试）")
    void sendFailure_propagates() {
        willThrow(new RuntimeException("kafka down")).given(mqSend).send(any());

        assertThatThrownBy(() -> service.handle(canal("sys_user", "UPDATE", row("id", "100"))))
                .isInstanceOf(RuntimeException.class);
    }

    // ---- helpers ----

    private List<CacheCompensationMQ.MsgPayload> capture(int times) {
        ArgumentCaptor<CacheCompensationMQ> captor = ArgumentCaptor.forClass(CacheCompensationMQ.class);
        verify(mqSend, times(times)).send(captor.capture());
        return captor.getAllValues().stream()
                .map(mq -> CacheCompensationMQ.parseMsg(mq.getMessage()))
                .collect(Collectors.toList());
    }

    @SafeVarargs
    private String canal(String table, String type, Map<String, String>... rows) {
        Map<String, Object> m = new HashMap<>();
        m.put("table", table);
        m.put("type", type);
        m.put("isDdl", false);
        m.put("es", 1749523200000L);
        m.put("data", Arrays.asList(rows));
        return JSON.toJSONString(m);
    }

    private Map<String, String> row(String k, String v) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put(k, v);
        return r;
    }
}
