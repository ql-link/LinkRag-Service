package com.qingluo.link.service;

public interface UserLoginEventRecorder {
    String SOURCE_LOGIN = "LOGIN";
    String SOURCE_REGISTER = "REGISTER";

    void record(Long userId, String source);
}
