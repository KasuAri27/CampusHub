package com.campushub.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 注册入参，字段名与必填性以 API 契约 2.2 为准。 */
@Data
public class RegisterRequest {
    /** 真实姓名明文；限长 20 是明文长度，落库为密文。 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 20, message = "姓名最长20个字符")
    private String name;

    @NotBlank(message = "学号不能为空")
    @Size(min = 4, max = 20, message = "学号长度为4至20位")
    private String studentId;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 发往校园邮箱的 6 位验证码。 */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 昵称必填；按显示宽度的限长由 {@link NicknameRule} 在服务层校验。 */
    @NotBlank(message = "昵称不能为空")
    private String nickname;

    /** 学院院系，注册时可不填，之后可在个人资料中补充或修改。 */
    @Size(max = 50, message = "学院名称最长50个字符")
    private String college;
}
