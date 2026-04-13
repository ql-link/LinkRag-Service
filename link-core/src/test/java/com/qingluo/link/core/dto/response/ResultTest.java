package com.qingluo.link.core.dto.response;

import com.qingluo.link.model.dto.response.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一响应包装测试
 */
class ResultTest {

    @Test
    void Should_CreateSuccessResult_When_CallSuccess() {
        Result<String> result = Result.success("test data");

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("test data", result.getData());
    }

    @Test
    void Should_CreateErrorResult_When_CallError() {
        Result<Object> result = Result.error(10001, "操作失败");

        assertEquals(10001, result.getCode());
        assertEquals("操作失败", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void Should_CreateResultWithData_When_CallOk() {
        Result<Integer> result = Result.ok(42);

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals(42, result.getData());
    }

    @Test
    void Should_CreateResultWithHttpStatus_When_CallErrorWithStatus() {
        Result<Object> result = Result.error(404, "资源不存在", 404);

        assertEquals(404, result.getCode());
        assertEquals("资源不存在", result.getMessage());
    }
}