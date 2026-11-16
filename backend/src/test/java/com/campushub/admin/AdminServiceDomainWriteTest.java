package com.campushub.admin;

import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.user.CreditService;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import com.campushub.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static com.campushub.support.BusinessFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** K1：后台封禁与信用分调整必须经域服务，不能直写 User。 */
class AdminServiceDomainWriteTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);
    private final CreditService creditService = mock(CreditService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private AdminService service;
    private User admin;

    @BeforeEach
    void setUp() {
        // 构造参数顺序与 AdminService 字段声明一致，与本测试无关的依赖传 null
        service = new AdminService(users, null, null, null, null, null, null,
                userService, creditService, null, null, null, notifications);
        admin = user(1, User.Role.admin);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        // 默认视为首次生效；重复提交场景在对应用例里单独改成 false
        when(creditService.adjust(anyLong(), anyInt(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void toggleStatusDelegatesToUserService() {
        service.toggleUserStatus(1L, 7L);

        verify(userService).toggleStatus(7L);
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonAdminCannotToggleStatus() {
        User normal = user(2, User.Role.user);
        when(users.findById(2L)).thenReturn(Optional.of(normal));

        assertThatThrownBy(() -> service.toggleUserStatus(2L, 7L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(403));
        verify(userService, never()).toggleStatus(anyLong());
    }

    @Test
    void adjustCreditGoesThroughCreditServiceWithUniqueKeys() {
        User target = user(7, User.Role.user);
        target.setCreditScore(60);
        when(users.findById(7L)).thenReturn(Optional.of(target));

        service.adjustCredit(1L, 7L, -10, null);
        service.adjustCredit(1L, 7L, -10, "  ");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(creditService, org.mockito.Mockito.times(2))
                .adjust(eq(7L), eq(-10), keys.capture(), anyString());
        // 每次人工调整是独立流水，键必须唯一且带 admin 前缀
        assertThat(keys.getAllValues()).allMatch(k -> k.startsWith("admin:7:"));
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
        // 不再直接写用户表
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adjustCreditAtUpperBoundNoLongerRejected() {
        User target = user(7, User.Role.user);
        target.setCreditScore(100);
        when(users.findById(7L)).thenReturn(Optional.of(target));

        service.adjustCredit(1L, 7L, 5, null);

        verify(creditService).adjust(eq(7L), eq(5), anyString(), anyString());
    }

    @Test
    void adjustCreditSendsChangeNotification() {
        User target = user(7, User.Role.user);
        target.setCreditScore(65);
        when(users.findById(7L)).thenReturn(Optional.of(target));

        service.adjustCredit(1L, 7L, -10, null);

        // 调整后分数由 CreditService 写入同一实体；此处 mock 不改实体，只验证 CREDIT_CHANGE 必发
        verify(notifications).createNotification(eq(7L), anyString(), anyString(), eq("CREDIT_CHANGE"));
    }

    @Test
    void missingTargetIs404() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adjustCredit(1L, 99L, 5, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(404));
        verify(creditService, never()).adjust(anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void clientOperationIdBecomesEventKey() {
        User target = user(7, User.Role.user);
        when(users.findById(7L)).thenReturn(Optional.of(target));

        service.adjustCredit(1L, 7L, -5, "op-123");

        verify(creditService).adjust(eq(7L), eq(-5), eq("admin:7:op-123"), anyString());
    }

    @Test
    void duplicateOperationDoesNotNotifyAgain() {
        User target = user(7, User.Role.user);
        when(users.findById(7L)).thenReturn(Optional.of(target));
        // CreditService 对同一 eventKey 的重复调用返回 false
        when(creditService.adjust(eq(7L), eq(-5), eq("admin:7:op-123"), anyString())).thenReturn(false);

        service.adjustCredit(1L, 7L, -5, "op-123");

        verify(notifications, never()).createNotification(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void invalidOperationIdIs400BeforeAnyWrite() {
        assertThatThrownBy(() -> service.adjustCredit(1L, 7L, -5, "a".repeat(37)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service.adjustCredit(1L, 7L, -5, "bad key!"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(400));
        verify(creditService, never()).adjust(anyLong(), anyInt(), anyString(), anyString());
    }
}
