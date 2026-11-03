package com.campushub.user;

import com.campushub.common.ApiResponse;
import com.campushub.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
/** 用户账号与认证控制器，提供学号注册、手机密码/短信登录、个人资料查看及密码更新。 */
public class UserController {

    private final UserService userService;

    /** 新用户注册：校验短信验证码与学号唯一性，手机号进行 AES 密文存储，并自动绑定校内邮箱。 */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(userService.register(request));
    }

    /** 账号密码登录：支持输入学号或 11 位手机号（内部自适应加密查验）。 */
    @PostMapping("/login/password")
    public ApiResponse<LoginResponse> loginWithPassword(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(userService.loginWithPassword(request));
    }

    @PostMapping("/login/sms")
    public ApiResponse<LoginResponse> loginWithSms(@Valid @RequestBody SmsLoginRequest request) {
        return ApiResponse.success(userService.loginWithSms(request));
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileDTO> getProfile() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(@RequestBody UserProfileDTO dto) {
        Long userId = UserContext.getCurrentUserId();
        userService.updateProfile(userId, dto);
        return ApiResponse.success("更新成功", null);
    }

    @PutMapping("/role")
    public ApiResponse<Void> updateRole(@RequestParam String role) {
        Long userId = UserContext.getCurrentUserId();
        userService.updateUserRole(userId, role);
        return ApiResponse.success("身份切换成功", null);
    }

    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(@Valid @RequestBody PasswordUpdateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        userService.updatePassword(userId, request);
        return ApiResponse.success("密码修改成功", null);
    }
}