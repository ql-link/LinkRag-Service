package com.qingluo.link.core.dto.response;

import com.qingluo.link.model.dto.response.PageResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageResult 分页响应测试
 */
class PageResultTest {

    @Test
    void Should_CreatePageResult_When_CreatedWithParams() {
        List<String> items = Arrays.asList("a", "b", "c");
        PageResult<String> result = new PageResult<>(items, 100, 1, 20);

        assertEquals(3, result.getItems().size());
        assertEquals(100, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(20, result.getPageSize());
    }

    @Test
    void Should_CalculateTotalPages_When_DataProvided() {
        PageResult<String> result = new PageResult<>(Arrays.asList("a"), 55, 1, 20);

        assertEquals(3, result.getTotalPages()); // 55 items / 20 per page = 3 pages
    }
}