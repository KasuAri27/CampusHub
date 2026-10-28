package com.campushub.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理后台用户列表响应 DTO（脱敏版）.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserManagementDTO {
    private Long id;
    private String name;
    private String nickname;
    private String email;
    /** 手机号脱敏：138****1234 */
    private String phoneMasked;
    private String role;
    private Integer creditScore;
    private Integer status;
    private LocalDateTime createTime;
}
