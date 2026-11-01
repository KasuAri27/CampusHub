package com.campushub.order;

import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.payment.PaymentAttemptRepository;
import com.campushub.complaint.ComplaintRepository;
import com.campushub.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import static com.campushub.support.BusinessFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证业务时间边界、争议冻结和幂等处罚，不通过 sleep 等待真实截止。 */
@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    @Mock CreditService credit;
    @Mock NotificationService notifications;
    @Mock PaymentAttemptRepository payments;
    @Mock ComplaintRepository complaints;
    @Mock OrderLocks locks;
    Order order;
    @BeforeEach void setup() {
        order = order();
        when(locks.lock(20L)).thenReturn(order);
    }
    OrderLifecycleService at(java.time.LocalDateTime time) {
        Clock clock = Clock.fixed(time.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
        return new OrderLifecycleService(orders, users, credit, notifications, clock, payments, complaints, locks);
    }
    @Test void onlyProviderCanSubmitAndDeadlineIs24Hours() {
        assertThatThrownBy(() -> at(NOW).submitCompletion(20L, 1L)).isInstanceOf(BusinessException.class);
        at(NOW).submitCompletion(20L, 2L);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.awaiting_acceptance);
        assertThat(order.getAcceptanceDueAt()).isEqualTo(NOW.plusHours(24));
        at(NOW.plusHours(1)).submitCompletion(20L, 2L);
        assertThat(order.getAcceptanceDueAt()).isEqualTo(NOW.plusHours(24));
    }
    @Test void manualAcceptanceStartsPaymentClockAndCannotBeRepeatedToExtendIt() {
        order.setStatus(Order.OrderStatus.awaiting_acceptance);
        assertThatThrownBy(() -> at(NOW).acceptCompletion(20L, 2L)).isInstanceOf(BusinessException.class);
        at(NOW).acceptCompletion(20L, 1L);
        at(NOW.plusMinutes(30)).acceptCompletion(20L, 1L);
        assertThat(order.getPaymentDueAt()).isEqualTo(NOW.plusHours(1));
        assertThat(order.getReviewEligibleAt()).isNull();
    }
    @Test void freeOrderIsReviewableAtAcceptance() {
        order.setAmountDue(java.math.BigDecimal.ZERO); order.setPaymentStatus(Order.PaymentStatus.not_required);
        order.setStatus(Order.OrderStatus.awaiting_acceptance);
        at(NOW).acceptCompletion(20L, 1L);
        assertThat(order.getReviewEligibleAt()).isEqualTo(NOW);
        assertThat(order.getPaymentDueAt()).isNull();
    }
    @Test void full49HourTimelineUsesEventTimeEvenWhenScannerRunsLate() {
        at(NOW).submitCompletion(20L, 2L);
        at(NOW.plusHours(24).minusNanos(1)).processDeadline(20L);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.awaiting_acceptance);
        at(NOW.plusHours(24)).processDeadline(20L);
        assertThat(order.getPaymentDueAt()).isEqualTo(NOW.plusHours(25));
        at(NOW.plusHours(25)).processDeadline(20L);
        at(NOW.plusHours(26)).processDeadline(20L);
        verify(credit, times(1)).adjust(eq(1L), eq(-10), eq("overdue:20"), anyString());
        assertThat(order.getEscalationDueAt()).isEqualTo(NOW.plusHours(49));
        assertThat(order.getEscalatedAt()).isNull();
        at(NOW.plusHours(49)).processDeadline(20L);
        assertThat(order.getEscalatedAt()).isEqualTo(NOW.plusHours(49));
    }
    @Test void disputeStopsAllTimersUntilAdministratorRules() {
        at(NOW).submitCompletion(20L, 2L);
        at(NOW.plusHours(1)).openDispute(20L, 1L, "未送达约定地点");
        at(NOW.plusDays(10)).processDeadline(20L);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.awaiting_acceptance);
        verifyNoInteractions(credit);
        when(users.findById(3L)).thenReturn(Optional.of(user(3, User.Role.admin)));
        when(complaints.findByOrderId(20L)).thenReturn(List.of());
        at(NOW.plusDays(10)).resolveDispute(20L, 3L, order.getAmountDue(), 2, "证据已核实，限两小时内付款");
        assertThat(order.isDisputed()).isFalse();
        assertThat(order.getPaymentDueAt()).isEqualTo(NOW.plusDays(10).plusHours(2));
    }
    @Test void paidOrLegacyOrdersNeverReceiveAutomaticDebtPenalty() {
        order.setStatus(Order.OrderStatus.completed); order.setPaymentStatus(Order.PaymentStatus.paid);
        order.setPaymentDueAt(NOW.minusDays(1)); at(NOW).processDeadline(20L);
        order.setAmountDue(null); at(NOW).processDeadline(20L);
        verifyNoInteractions(credit);
    }
}
