package com.campushub.user;

import com.campushub.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static com.campushub.support.BusinessFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 封禁/解封切换：必须经悲观锁读取，状态结果可直接断言。 */
class UserServiceStatusTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users, null, null, null, null);

    @ParameterizedTest
    @CsvSource({"1,0", "0,1"})
    void togglesStatusUnderLock(int before, int expected) {
        User target = user(7, User.Role.user);
        target.setStatus(before);
        when(users.findLockedById(7L)).thenReturn(Optional.of(target));

        assertThat(service.toggleStatus(7L)).isEqualTo(expected);
        assertThat(target.getStatus()).isEqualTo(expected);
        // 不允许绕过加锁读取：普通 findById 不应被用来取目标用户。
        verify(users, never()).findById(7L);
    }

    @Test
    void nullStatusIsTreatedAsBanned() {
        User target = user(7, User.Role.user);
        target.setStatus(null);
        when(users.findLockedById(7L)).thenReturn(Optional.of(target));

        assertThat(service.toggleStatus(7L)).isEqualTo(1);
    }

    @Test
    void missingUserIs404() {
        when(users.findLockedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleStatus(99L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(404));
    }
}
