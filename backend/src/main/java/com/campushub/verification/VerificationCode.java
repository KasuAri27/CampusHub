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

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private String type;  // REGISTER, LOGIN, BIND

    @Column(nullable = false)
    private LocalDateTime expireTime;

    private boolean used = false;

    @CreationTimestamp
    private LocalDateTime createTime;
}