package com.campushub.verification;

import com.campushub.common.BusinessException;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import com.campushub.util.AesUtil;
import com.campushub.util.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 验证码生命周期管理服务。
 * 负责验证码安全生成（SecureRandom）、旧码失效覆盖、5分钟有效期校验及校园邮箱模拟发送。
 */
public class VerificationCodeService {
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final AesUtil aesUtil;

    @Transactional
    public String sendCode(String phone, String type, String studentId) {
        if (!"REGISTER".equals(type) && !"LOGIN".equals(type)) throw new BusinessException(400, "验证码用途不合法");
        if ("REGISTER".equals(type) && (studentId == null || studentId.length() < 4 || studentId.length() > 20))
            throw new BusinessException(400, "请填写4至20位学号");
        // 1. 使同手机号同类型的旧验证码失效
        List<VerificationCode> oldCodes = verificationCodeRepository
                .findByPhoneAndTypeAndUsedFalse(phone, type);
        if (!oldCodes.isEmpty()) {
            oldCodes.forEach(vc -> vc.setUsed(true));
            verificationCodeRepository.saveAll(oldCodes);
            log.info("已使 {} 条旧验证码失效", oldCodes.size());
        }

        // 2. 生成新验证码
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(5);

        VerificationCode vc = new VerificationCode();
        vc.setPhone(phone);
        vc.setStudentId(studentId);
        vc.setCode(code);
        vc.setType(type);
        vc.setExpireTime(expireTime);
        vc.setUsed(false);
        verificationCodeRepository.save(vc);

        // 3. 控制台打印（方便调试）
        // 验证码只通过验证渠道发送，不再输出到日志或公开 HTTP 响应。

        // 4. 尝试发送邮件
        String email = null;
        if ("REGISTER".equals(type) && studentId != null && !studentId.isEmpty()) {
            email = studentId + "@smail.nju.edu.cn";
            log.info("注册场景，根据学号构造邮箱: {}", email);
        } else if ("LOGIN".equals(type)) {
            String encryptedPhone = aesUtil.encrypt(phone);
            Optional<User> userOpt = userRepository.findByPhone(encryptedPhone);
            if (userOpt.isPresent() && userOpt.get().getEmail() != null) {
                email = userOpt.get().getEmail();
                log.info("登录场景，从数据库查到邮箱: {}", email);
            }
        }

        if (email != null) {
            try {
                mailService.sendVerificationCode(email, code);
                log.info("验证码邮件已发送至: {}", email);
            } catch (Exception e) {
                log.error("邮件发送失败: {}", e.getMessage(), e);
                throw new BusinessException(503, "验证码邮件暂无法发送，请稍后重试");
            }
        } else {
            throw new BusinessException(400, "未找到已注册校邮，请先完成注册");
        }

        return code;
    }

    public void verifyCode(String phone, String code, String type) {
        verifyCode(phone, code, type, null);
    }
    public void verifyCode(String phone, String code, String type, String studentId) {
        LocalDateTime now = LocalDateTime.now();
        VerificationCode vc = verificationCodeRepository
                .findByPhoneAndCodeAndTypeAndUsedFalseAndExpireTimeAfter(phone, code, type, now)
                .orElseThrow(() -> new BusinessException("验证码无效或已过期"));
        if ("REGISTER".equals(type) && !java.util.Objects.equals(studentId, vc.getStudentId()))
            throw new BusinessException(400, "验证码与注册学号不匹配");
        vc.setUsed(true);
        verificationCodeRepository.save(vc);
    }
}
