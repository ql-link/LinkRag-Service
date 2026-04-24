package com.qingluo.link.model.enums;

/**
 * 用户角色枚举
 */
public enum UserRole {

    ADMIN,
    USER;

    /**
     * 从字符串安全转换，null 默认返回 USER
     *
     * @param value 角色字符串
     * @return 对应枚举值
     * @throws IllegalArgumentException 当传入无效角色时
     */
    public static UserRole of(String value) {
        if (value == null) {
            return USER;
        }
        for (UserRole role : values()) {
            if (role.name().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("无效的用户角色: " + value);
    }
}
