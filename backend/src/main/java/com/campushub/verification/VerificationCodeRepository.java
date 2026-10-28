package com.campushub.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findByPhoneAndCodeAndTypeAndUsedFalseAndExpireTimeAfter(
            String phone, String code, String type, LocalDateTime now);

    // 新增方法：根据手机号、类型和未使用状态查找所有验证码
    List<VerificationCode> findByPhoneAndTypeAndUsedFalse(String phone, String type);
}