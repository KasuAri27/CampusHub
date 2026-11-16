package com.campushub.user;

import com.campushub.util.AesUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** 姓名加密转换器：密文带前缀且每次不同，历史明文原样返回，解不开时不抛异常。 */
class EncryptedNameConverterTest {
    private static EncryptedNameConverter converterWithKey(String key) {
        AesUtil aes = new AesUtil();
        ReflectionTestUtils.setField(aes, "secretKey", key);
        return new EncryptedNameConverter(aes);
    }

    private final EncryptedNameConverter converter = converterWithKey("0123456789abcdef0123456789abcdef");

    @Test void encryptsWithRandomIvAndRoundTrips() {
        String first = converter.convertToDatabaseColumn("测试同学");
        String second = converter.convertToDatabaseColumn("测试同学");
        assertThat(first).startsWith("gcm:").doesNotContain("测试同学");
        assertThat(second).isNotEqualTo(first);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo("测试同学");
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo("测试同学");
    }

    @Test void longestNameFitsColumnWidth() {
        // 20 个汉字是明文上限，其密文必须放得进 255 的列宽。
        assertThat(converter.convertToDatabaseColumn("一二三四五六七八九十一二三四五六七八九十")).hasSizeLessThanOrEqualTo(255);
    }

    @Test void legacyPlaintextAndNullPassThrough() {
        assertThat(converter.convertToEntityAttribute("测试用户1")).isEqualTo("测试用户1");
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test void wrongKeyOrTamperedCiphertextShowsPlaceholder() {
        String stored = converter.convertToDatabaseColumn("测试同学");
        assertThat(converterWithKey("fedcba9876543210fedcba9876543210").convertToEntityAttribute(stored)).isEqualTo("***");
        assertThat(converter.convertToEntityAttribute("gcm:not-a-valid-ciphertext")).isEqualTo("***");
    }
}
