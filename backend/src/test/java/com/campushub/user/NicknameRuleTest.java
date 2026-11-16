package com.campushub.user;

import com.campushub.common.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 昵称限长的边界：恰好 10 个汉字或 20 个英文字符通过，多一个就拒绝。 */
class NicknameRuleTest {
    @ParameterizedTest
    @ValueSource(strings = {"a", "abcdefghijklmnopqrst", "一二三四五六七八九十", "一二三四五abcdefghij", "User2418"})
    void acceptsNicknameWithinDisplayWidth(String nickname) {
        assertThatCode(() -> NicknameRule.check(nickname)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abcdefghijklmnopqrstu", "一二三四五六七八九十一", "一二三四五abcdefghijk"})
    void rejectsBlankOrOverlongNickname(String nickname) {
        assertThatThrownBy(() -> NicknameRule.check(nickname))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(400);
    }
}
