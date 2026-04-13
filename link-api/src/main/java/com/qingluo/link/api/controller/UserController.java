package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/profile")
    @SaCheckLogin
    public Result<UserProfileDTO> getProfile() {
        // TODO: 调用 UserService.getProfile()
        return Result.success(new UserProfileDTO());
    }
}