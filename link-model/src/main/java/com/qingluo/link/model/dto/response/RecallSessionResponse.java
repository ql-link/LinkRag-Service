package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 召回 session token 签发响应（LINK-104）。
 *
 * <p>前端凭 {@code token} 直连 Python {@code streamUrl}（{@code POST}，token 走 Authorization 头）拉召回 SSE。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "召回 session token 签发响应")
public class RecallSessionResponse {

    @Schema(description = "短期 session token（HS256 JWT）")
    private String token;

    @Schema(description = "token 有效期（秒）", example = "30")
    private long expiresIn;

    @Schema(description = "前端直连 Python RAG 流式问答 SSE 的地址", example = "https://rag.example.com/api/v1/rag/stream")
    private String streamUrl;
}
