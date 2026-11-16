package com.campushub.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 个人资料，既是查询的返回体，也是修改的请求体。
 * 修改时只有昵称、头像、学院会被采纳；学号、邮箱等其余字段注册后不可更改，即使传入也忽略。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private Long id;
    private String studentId;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    /** 学院院系；修改时传空字符串表示清空。 */
    @Size(max = 50, message = "学院名称最长50个字符")
    private String college;
    private Integer creditScore;
    private String role;
    private boolean restricted;
}
