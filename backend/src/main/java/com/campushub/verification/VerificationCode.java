package com.campushub.verification;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "verification_codes")
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 11)
    private String phone;
    /** 注册验证码绑定申请时的学号，不能收取A校邮验证码后注册成B学号。 */
    @Column(length = 20)
    private String studentId;

    @Column(nullable = false, length = 6)
    private String code;

    /** 用途：REGISTER（注册）或 LOGIN（验证码登录）；服务层只接受这两种取值。 */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    private boolean used = false;

    @CreationTimestamp
    private LocalDateTime createTime;
}
