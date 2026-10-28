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

    public void sendVerificationCode(String toEmail, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("校园互助平台 - 验证码");
            message.setText("您的验证码是：" + code + "，有效期5分钟。请勿泄露给他人。");
            mailSender.send(message);
            log.info("验证码邮件已发送至: {}", toEmail);
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage());
        }
    }
}