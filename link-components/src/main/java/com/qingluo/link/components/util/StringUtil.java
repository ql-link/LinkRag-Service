package com.qingluo.link.components.util;

import java.util.UUID;

/**
 * 字符串工具类
 */
public class StringUtil {

    private StringUtil() {}

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串是否为空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空白
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 生成 UUID
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成简短的 UUID
     */
    public static String generateShortUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 字符串脱敏（保留前4位和后4位）
     */
    public static String mask(String str) {
        if (isEmpty(str)) {
            return str;
        }
        if (str.length() <= 8) {
            return "****";
        }
        return str.substring(0, 4) + "****" + str.substring(str.length() - 4);
    }

    /**
     * 字符串脱敏（邮箱）
     */
    public static String maskEmail(String email) {
        if (isEmpty(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String local = parts[0];
        if (local.length() <= 2) {
            return "*@" + parts[1];
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + parts[1];
    }

    /**
     * 左填充
     */
    public static String leftPad(String str, int length, char pad) {
        if (str == null) {
            str = "";
        }
        if (str.length() >= length) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length - str.length(); i++) {
            sb.append(pad);
        }
        sb.append(str);
        return sb.toString();
    }
}