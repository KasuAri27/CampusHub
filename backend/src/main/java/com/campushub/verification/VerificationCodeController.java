package com.campushub.verification;

import com.campushub.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/verification")
@RequiredArgsConstructor
public class VerificationCodeController {

    private final VerificationCodeService verificationCodeService;

    @PostMapping("/send")
    public ApiResponse<String> sendCode(@RequestBody SendCodeRequest request) {
        // 调用 Service，传入 phone, type, studentId（可能为 null）
        String code = verificationCodeService.sendCode(
                request.getPhone(),
                request.getType(),
                request.getStudentId()
        );
        // 生产环境不要返回验证码，这里仅用于调试
        return ApiResponse.success("验证码已发送", code);
    }

    @Data
    public static class SendCodeRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
        private String phone;

        @NotBlank(message = "类型不能为空")
        private String type; // REGISTER, LOGIN

        // 新增字段：学号（注册时传入，登录时可不传）
        private String studentId;
    }
}