package com.campushub.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;
    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}")
    private String from;

    public void sendVerificationCode(String toEmail, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from == null || from.isBlank()) throw new IllegalStateException("未配置邮件发送账号");
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("校园互助平台 - 验证码");
            message.setText("您的验证码是：" + code + "，有效期5分钟。请勿泄露给他人。");
            mailSender.send(message);
            log.info("验证码邮件已发送至: {}", toEmail);
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage());
            throw new com.campushub.common.BusinessException(503, "验证码邮件暂无法发送，请稍后重试");
        }
    }
}
