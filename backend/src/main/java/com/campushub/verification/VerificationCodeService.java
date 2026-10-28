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
public class VerificationCodeService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final AesUtil aesUtil;

    @Transactional
    public String sendCode(String phone, String type, String studentId) {
        // 1. 使同手机号同类型的旧验证码失效
        List<VerificationCode> oldCodes = verificationCodeRepository
                .findByPhoneAndTypeAndUsedFalse(phone, type);
        if (!oldCodes.isEmpty()) {
            oldCodes.forEach(vc -> vc.setUsed(true));
            verificationCodeRepository.saveAll(oldCodes);
            log.info("已使 {} 条旧验证码失效", oldCodes.size());
        }

        // 2. 生成新验证码
        String code = String.format("%06d", new Random().nextInt(999999));
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(5);

        VerificationCode vc = new VerificationCode();
        vc.setPhone(phone);
        vc.setCode(code);
        vc.setType(type);
        vc.setExpireTime(expireTime);
        vc.setUsed(false);
        verificationCodeRepository.save(vc);

        // 3. 控制台打印（方便调试）
        System.out.println("【验证码】" + phone + " 验证码：" + code + "，有效期5分钟");

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
                // 邮件发送失败不影响主流程，但可提示用户查看控制台验证码
            }
        } else {
            log.info("未找到邮箱，仅发送短信验证码");
        }

        return code;
    }

    public void verifyCode(String phone, String code, String type) {
        LocalDateTime now = LocalDateTime.now();
        VerificationCode vc = verificationCodeRepository
                .findByPhoneAndCodeAndTypeAndUsedFalseAndExpireTimeAfter(phone, code, type, now)
                .orElseThrow(() -> new BusinessException("验证码无效或已过期"));
        vc.setUsed(true);
        verificationCodeRepository.save(vc);
    }
}