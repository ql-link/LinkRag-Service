package com.qingluo.link.service.recall;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.security.RecallSessionJwtSigner;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.request.RecallSessionRequest;
import com.qingluo.link.model.dto.response.RecallSessionResponse;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.RecallSessionService;
import com.qingluo.link.service.config.RecallProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 召回 session token 签发编排（LINK-104）。
 *
 * <p>同步流程：用户状态校验 → 数据集归属校验 → 签发 token → 组装响应。任一校验失败抛 {@link BusinessException}
 * 由全局异常处理器转 HTTP 错误（未签发 token）。</p>
 *
 * <p>护栏（brief 决策⑤）：复用 {@code assertUserActive}（封禁用户拒签）；<b>不加限流</b>——签发本身不消耗 Python
 * 资源，资源滥用由 Python「按用户并发上限」兜底。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RecallSessionServiceImpl implements RecallSessionService {

    private static final String STREAM_PATH = "/api/v1/recall/stream";

    private final SysUserMapper sysUserMapper;
    private final RecallScopeResolver scopeResolver;
    private final RecallSessionJwtSigner sessionJwtSigner;
    private final RecallProperties properties;

    @Override
    public RecallSessionResponse issue(Long userId, RecallSessionRequest request) {
        // sub 必须为正整数（Python 对 sub<=0 拒绝）。
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.AUTH_DISABLED);
        }
        // 护栏：封禁用户（status != 1）拒绝签发。
        assertUserActive(userId);

        // 归属校验：datasetIds 由 DTO @NotEmpty 保证非空，走 resolver 的归属校验分支
        // （全部归属当前用户且未软删，否则抛 RECALL_SCOPE_FORBIDDEN）；不会触发空入参全库展开。
        ResolvedScope scope = scopeResolver.resolve(userId, request.getDatasetIds());

        // claim dataset_ids 写已校验的显式 id，绝不为空（杜绝 Python 误判全库授权）。
        String token = sessionJwtSigner.sign(userId, scope.datasetIds(), Instant.now());
        return new RecallSessionResponse(token, properties.getSessionJwtExpSeconds(), buildStreamUrl());
    }

    /**
     * 复用登录态用户状态：status != 1 视为不可用，拒绝签发（与内部召回链路一致）。
     */
    private void assertUserActive(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_DISABLED);
        }
    }

    /**
     * 前端可见的 Python 直连地址 = sessionStreamBaseUrl + /api/v1/recall/stream。
     */
    private String buildStreamUrl() {
        String base = properties.getSessionStreamBaseUrl();
        if (base == null) {
            base = "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + STREAM_PATH;
    }
}
