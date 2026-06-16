package com.qingluo.link.model.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.Pattern;
import lombok.Data;

/**
 * PDF 解析配置（1 项），字段名与 Python {@code PDFConfig} 对齐。
 *
 * <p>白名单校验拦截非法后端值（Python 无 enum 校验但消费侧只认这几种）；{@code @Pattern} 对 null 放行，
 * 即「本次未提交该字段」，由 Python 回退 {@code settings.PDF_PARSER_BACKEND}。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "PDF 解析配置")
public class PdfConfig {

    @Pattern(regexp = "auto|mineru|opendataloader|naive",
        message = "pdf_parser_backend 仅支持 auto/mineru/opendataloader/naive")
    @Schema(description = "PDF 解析后端", example = "mineru",
        allowableValues = {"auto", "mineru", "opendataloader", "naive"})
    private String pdfParserBackend;
}
