package com.campushub.user;

import com.campushub.util.AesUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 真实姓名的落库加解密转换器（API 契约 2.2：姓名 AES-256-GCM 加密存储）。
 * <p>
 * 挂在 {@link User#getName()} 对应的字段上，由 JPA 在读写 {@code users.name} 时自动调用：
 * 实体属性始终是明文，其他集群直接 {@code getName()} 即可，不需要也不应该自行解密。
 * </p>
 * <p>
 * 库内格式为 {@code gcm:} + Base64 密文。前缀用来区分加密上线前留下的明文姓名：
 * 不带前缀的值按历史明文原样返回，不做解密。历史行不会被自动改写成密文
 * （姓名没有修改入口，JPA 只在属性变化时更新该列）。
 * </p>
 * <p>
 * 带前缀却解不开（密钥配置错误或密文损坏）时返回 {@code ***} 而不是抛异常，
 * 与手机号解密失败的展示保持一致，避免一次密钥配置错误让所有读取用户的接口全部失败。
 * </p>
 */
@Slf4j
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedNameConverter implements AttributeConverter<String, String> {

    /** 密文前缀，同时充当格式版本标识。 */
    static final String PREFIX = "gcm:";
    /** 解密失败时的占位显示。 */
    static final String UNREADABLE = "***";

    private final AesUtil aesUtil;

    @Override
    public String convertToDatabaseColumn(String name) {
        return name == null ? null : PREFIX + aesUtil.encryptGcm(name);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            return aesUtil.decryptGcm(stored.substring(PREFIX.length()));
        } catch (RuntimeException e) {
            // 不记录密文和明文，只提示排查密钥配置。
            log.warn("用户姓名解密失败，请核对 AES 密钥配置");
            return UNREADABLE;
        }
    }
}
