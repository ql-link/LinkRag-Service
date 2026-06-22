package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import com.qingluo.link.model.dto.request.UpdateProfileRequest;
import com.qingluo.link.model.dto.response.AuthResult;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     */
    AuthResult login(LoginRequest request);

    /**
     * 用户注册
     */
    AuthResult register(RegisterRequest request);

    /**
     * 用户登出
     */
    void logout();

    /**
     * 获取用户信息
     */
    UserProfileDTO getProfile(Long userId);

    /**
     * 更新个人资料
     */
    void updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 上传并更新用户头像
     */
    UserProfileDTO uploadAvatar(Long userId, MultipartFile file);
}
