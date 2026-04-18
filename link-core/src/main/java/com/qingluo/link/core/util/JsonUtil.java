package com.qingluo.link.core.util;

import com.alibaba.fastjson.JSON;

/**
 * JSON 工具类
 */
public class JsonUtil {

    public static String toJson(Object obj) {
        return JSON.toJSONString(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }
}