package com.qingluo.link.core.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.enums.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessDetailsAreReturnedAsSafeStructuredData() {
        BusinessException exception = new BusinessException(
            ErrorCode.INVALID_DATASET_MODEL_BINDING,
            Map.of("field", "dense_embedding_config_id"));

        ResponseEntity<Result<Object>> response =
            handler.handleBusinessException(exception, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData())
            .isEqualTo(Map.of("field", "dense_embedding_config_id"));
    }

    @Test
    void businessExceptionWithoutDetailsKeepsNullDataShape() {
        ResponseEntity<Result<Object>> response = handler.handleBusinessException(
            new BusinessException(ErrorCode.LLM_CONFIG_NOT_FOUND), null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNull();
    }
}
