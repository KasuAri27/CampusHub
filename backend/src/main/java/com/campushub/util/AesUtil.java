package com.campushub.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 对称加解密工具类，用于敏感个人信息的落库加密与读取解密。
 * <p>
 * 提供两套互不兼容的密文格式，按是否需要“按密文查找”选择：
 * <ul>
 * <li>{@link #encrypt}/{@link #decrypt}：确定性加密，同一明文的密文固定。手机号用它，
 *     因为库表要对密文做唯一约束、登录要按密文等值查找。</li>
 * <li>{@link #encryptGcm}/{@link #decryptGcm}：AES-GCM，每次随机 IV，带完整性校验。
 *     真实姓名用它；密文每次不同，不能用于唯一约束或等值查找。</li>
 * </ul>
 * 两套方法共用同一把密钥；密钥为 32 字节时即 AES-256。
 * </p>
 */
@Component
public class AesUtil {

    @Value("${aes.secret-key}")
    private String secretKey;

    private static final String ALGORITHM = "AES";
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    /** GCM 推荐的 IV 长度：96 位。 */
    private static final int GCM_IV_BYTES = 12;
    /** GCM 认证标签长度：128 位。 */
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretKeySpec getKeySpec() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        // 密钥长度必须是16、24或32字节
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new RuntimeException("AES密钥长度必须为16、24或32字节，当前为" + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /** 确定性加密：同一明文结果固定，可用于唯一约束和按密文查找。输出为 Base64。 */
    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKeySpec());
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /** 解密 {@link #encrypt} 产生的密文。 */
    public String decrypt(String cipherText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKeySpec());
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }

    /**
     * AES-GCM 加密。每次生成随机 IV，同一明文的密文各不相同。
     *
     * @return Base64(IV ‖ 密文 ‖ 认证标签)；IV 固定占前 12 字节，解密时从中取回
     */
    public String encryptGcm(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKeySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM加密失败", e);
        }
    }

    /**
     * 解密 {@link #encryptGcm} 产生的密文。
     * 密钥不符、密文被篡改或格式不对时认证标签校验失败，统一抛出运行时异常。
     */
    public String decryptGcm(String cipherText) {
        try {
            byte[] input = Base64.getDecoder().decode(cipherText);
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKeySpec(), new GCMParameterSpec(GCM_TAG_BITS, input, 0, GCM_IV_BYTES));
            byte[] decrypted = cipher.doFinal(input, GCM_IV_BYTES, input.length - GCM_IV_BYTES);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM解密失败", e);
        }
    }
}
