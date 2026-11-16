package com.campushub.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册与登录的成功响应，结构以 API 契约 2.2、2.3 为准：{@code token}、{@code role}、{@code user} 三段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    /** JWT，后续请求放在 Authorization: Bearer 头中。 */
    private String token;
    /** 账号角色：user 或 admin。 */
    private String role;
    /** 当前用户的基本信息。 */
    private UserInfo user;

    /** 登录后前端立即需要的用户基本信息；不含手机号、邮箱等敏感字段，完整资料走个人资料接口。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String studentId;
        private String nickname;
        private String avatar;
        private String college;
        private Integer creditScore;
        /** 账号是否被封禁而处于受限登录：为 true 时只能处理本人订单、付款、投诉和账号安全。 */
        private boolean restricted;
    }
}
