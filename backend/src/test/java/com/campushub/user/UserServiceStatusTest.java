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

/** 账号状态写入口：toggleStatus 切换与 ban 幂等封禁都必须经悲观锁读取，状态结果可直接断言。 */
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

    // ---- ban（K9）：幂等封禁，只封不解 ----

    private User admin() {
        User admin = user(1, User.Role.admin);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        return admin;
    }

    @Test
    void banRejectsNonAdminOperatorWith403() {
        User ordinary = user(2, User.Role.user);
        when(users.findById(2L)).thenReturn(Optional.of(ordinary));
        User target = user(7, User.Role.user);
        when(users.findLockedById(7L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.ban(7L, 2L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(403));
        assertThat(target.getStatus()).isEqualTo(1);
    }

    @Test
    void banRejectsUnknownOrMissingOperatorWith403() {
        when(users.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ban(7L, 404L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(403));
        assertThatThrownBy(() -> service.ban(7L, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(403));
        // 操作者无效时不应去锁目标用户。
        verify(users, never()).findLockedById(7L);
    }

    @Test
    void banRejectsAdminTargetWith400() {
        admin();
        User otherAdmin = user(3, User.Role.admin);
        when(users.findLockedById(3L)).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> service.ban(3L, 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(400));
        assertThat(otherAdmin.getStatus()).isEqualTo(1);
    }

    @Test
    void banIsIdempotentAndNeverUnbans() {
        admin();
        User target = user(7, User.Role.user);
        when(users.findLockedById(7L)).thenReturn(Optional.of(target));

        assertThat(service.ban(7L, 1L)).isTrue();
        assertThat(target.getStatus()).isZero();
        // 第二次调用：返回 false，状态仍为封禁（toggleStatus 在这里会把人解封，ban 不会）。
        assertThat(service.ban(7L, 1L)).isFalse();
        assertThat(target.getStatus()).isZero();
    }

    @Test
    void banDoesNotTouchCreditScore() {
        admin();
        User target = user(7, User.Role.user);
        target.setCreditScore(63);
        when(users.findLockedById(7L)).thenReturn(Optional.of(target));

        service.ban(7L, 1L);

        assertThat(target.getCreditScore()).isEqualTo(63);
    }

    @Test
    void banMissingTargetIs404() {
        admin();
        when(users.findLockedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ban(99L, 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(404));
    }
}
