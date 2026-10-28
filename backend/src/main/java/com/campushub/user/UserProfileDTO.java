package com.campushub.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Integer creditScore;
    private String role;
}
