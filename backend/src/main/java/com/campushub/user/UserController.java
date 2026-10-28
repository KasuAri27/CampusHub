package com.campushub.user;

import com.campushub.common.ApiResponse;
import com.campushub.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(userService.register(request));
    }

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
    public ApiResponse<Void> updateRole(@RequestParam String role,
                                        @RequestHeader("X-User-Id") Long userId) {
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