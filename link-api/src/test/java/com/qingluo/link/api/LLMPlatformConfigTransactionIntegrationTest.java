package com.qingluo.link.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.request.AdminPlatformConfigSaveRequest;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityDefaultService;
import com.qingluo.link.service.LLMModelConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 管理端平台配置与默认指针必须由同一事务提交，禁止留下半完成配置。
 */
@SpringBootTest
class LLMPlatformConfigTransactionIntegrationTest {

    private static final long PROVIDER_ID = 99801L;
    private static final long MODEL_ID = 99802L;

    @Autowired
    private LLMModelConfigService configService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private LLMCapabilityDefaultService defaultService;

    @BeforeEach
    void setUp() {
        cleanFixture();
        jdbcTemplate.update("""
            INSERT INTO llm_system_provider (
                id, provider_type, provider_name, api_base_url, default_protocol,
                is_active, priority
            ) VALUES (?, 'tx-rollback', 'Transaction Rollback',
                'https://example.test/v1', 'openai', true, 50)
            """, PROVIDER_ID);
        jdbcTemplate.update("""
            INSERT INTO llm_provider_model (
                id, provider_id, model_name, display_name, capability, protocol,
                api_base_url, is_active
            ) VALUES (?, ?, 'tx-chat', 'Transaction Chat', 'CHAT', 'openai',
                'https://example.test/v1/chat/completions', true)
            """, MODEL_ID, PROVIDER_ID);
    }

    @AfterEach
    void tearDown() {
        cleanFixture();
    }

    @Test
    void defaultWriteFailureRollsBackNewPlatformConfig() {
        // 新配置的 ID 由数据库生成，无法提前精确 stub；匹配任意正 ID。
        given(defaultService.setSystemDefault(
            org.mockito.ArgumentMatchers.eq("CHAT"),
            org.mockito.ArgumentMatchers.longThat(id -> id != null && id > 0)))
            .willThrow(new BusinessException(ErrorCode.LLM_DEFAULT_UPDATE_FAILED));

        AdminPlatformConfigSaveRequest request = new AdminPlatformConfigSaveRequest();
        request.setSourceProviderModelId(MODEL_ID);
        request.setApiKey("platform-secret");
        request.setSetAsDefault(true);

        assertThatThrownBy(() -> configService.saveSystemConfig(null, request))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(ErrorCode.LLM_DEFAULT_UPDATE_FAILED.getCode()));

        Integer configCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM llm_model_config
            WHERE scope = 'SYSTEM' AND owner_user_id = 0
              AND provider_id = ? AND model_name = 'tx-chat' AND capability = 'CHAT'
            """, Integer.class, PROVIDER_ID);
        assertThat(configCount).isZero();
    }

    private void cleanFixture() {
        jdbcTemplate.update("DELETE FROM llm_model_config WHERE provider_id = ?", PROVIDER_ID);
        jdbcTemplate.update("DELETE FROM llm_provider_model WHERE id = ?", MODEL_ID);
        jdbcTemplate.update("DELETE FROM llm_system_provider WHERE id = ?", PROVIDER_ID);
    }
}
