package com.qingluo.link.core.util;

/**
 * 数值工具类
 */
public class NumberUtil {

    private NumberUtil() {
    }

    /**
     * Integer 为 null 时返回 0，否则返回其原始值。
     *
     * <p>用于落库 token 等数值列：上游缺省字段统一落 0，避免 NOT NULL 列写入 null。</p>
     */
    public static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
