package com.campushub.payment;

import com.campushub.order.*;
import com.campushub.user.*;
import com.campushub.notification.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import static com.campushub.support.BusinessFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验签通过仍必须核对本地账单身份和金额；签名不能代替业务一致性校验。 */
@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {
    @Mock OrderRepository orders;
    @Mock PaymentAttemptRepository attempts;
    @Mock NotificationService notifications;
    @Mock CreditService credit;
    @Mock CreditEventRepository events;
    PaymentTransactionService service;
    com.campushub.order.Order order;
    PaymentAttempt attempt;
    @BeforeEach void setup() {
        order = order(); order.setStatus(com.campushub.order.Order.OrderStatus.completed); order.setPaymentDueAt(NOW.plusHours(1));
        attempt = new PaymentAttempt(); attempt.setId(7L); attempt.setOrderId(20L); attempt.setOutTradeNo("out");
        attempt.setChannel(PaymentAttempt.Channel.WECHAT); attempt.setAppId("app"); attempt.setMerchantId("merchant"); attempt.setAmount(new BigDecimal("6.50"));
        when(attempts.findByOutTradeNo("out")).thenReturn(Optional.of(attempt));
        when(orders.findLockedById(20L)).thenReturn(Optional.of(order)); when(attempts.findLockedById(7L)).thenReturn(Optional.of(attempt));
        service = new PaymentTransactionService(orders, attempts, new PaymentProperties(), notifications, credit, events, CLOCK);
    }
    static Stream<PaymentGateway.VerifiedPayment> invalidResults() {
        return Stream.of(
            result(PaymentAttempt.Channel.ALIPAY,"6.50","CNY","app","merchant","tx",NOW),
            result(PaymentAttempt.Channel.WECHAT,"0.01","CNY","app","merchant","tx",NOW),
            result(PaymentAttempt.Channel.WECHAT,"6.50","USD","app","merchant","tx",NOW),
            result(PaymentAttempt.Channel.WECHAT,"6.50","CNY","other","merchant","tx",NOW),
            result(PaymentAttempt.Channel.WECHAT,"6.50","CNY","app","other","tx",NOW),
            result(PaymentAttempt.Channel.WECHAT,"6.50","CNY","app","merchant","",NOW),
            result(PaymentAttempt.Channel.WECHAT,"6.50","CNY","app","merchant","tx",null));
    }
    static PaymentGateway.VerifiedPayment result(PaymentAttempt.Channel channel, String amount, String currency,
            String app, String merchant, String transaction, java.time.LocalDateTime paidAt) {
        return new PaymentGateway.VerifiedPayment(channel,"out",transaction,new BigDecimal(amount),currency,app,merchant,true,paidAt);
    }
    @ParameterizedTest @MethodSource("invalidResults")
    void rejectsInconsistentButSignedResults(PaymentGateway.VerifiedPayment result) {
        assertThatThrownBy(() -> service.recordVerified(result)).isInstanceOf(com.campushub.common.BusinessException.class);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttempt.Status.PENDING);
        assertThat(order.getPaymentStatus()).isEqualTo(com.campushub.order.Order.PaymentStatus.unpaid);
        verifyNoInteractions(notifications, credit);
    }
    @Test void duplicateValidNotificationNeverDuplicatesBusinessEffects() {
        var result = result(PaymentAttempt.Channel.WECHAT,"6.50","CNY","app","merchant","tx",NOW);
        service.recordVerified(result); service.recordVerified(result);
        assertThat(order.getPaymentStatus()).isEqualTo(com.campushub.order.Order.PaymentStatus.paid);
        assertThat(order.getSettlementStatus()).isEqualTo(com.campushub.order.Order.SettlementStatus.pending);
        verify(notifications, times(2)).createNotification(anyLong(), anyString(), anyString(), anyString());
    }
    @Test void verifiedPaymentDuringDisputeIsRecordedButDoesNotOpenReview() {
        order.setDisputed(true);
        service.recordVerified(result(PaymentAttempt.Channel.WECHAT,"6.50","CNY","app","merchant","tx",NOW));
        assertThat(order.getPaymentStatus()).isEqualTo(com.campushub.order.Order.PaymentStatus.paid);
        assertThat(order.getReviewEligibleAt()).isNull(); assertThat(order.isDisputed()).isTrue();
    }
}
