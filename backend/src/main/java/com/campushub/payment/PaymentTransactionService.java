package com.campushub.payment;

import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.order.Order;
import com.campushub.order.OrderRepository;
import com.campushub.user.CreditService;
import com.campushub.user.CreditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** 短事务只负责预留支付单、校验金额和记账；外部网络调用放在 PaymentService 中。 */
@Service @RequiredArgsConstructor
public class PaymentTransactionService {
    private final OrderRepository orders;
    private final PaymentAttemptRepository attempts;
    private final PaymentProperties properties;
    private final NotificationService notifications;
    private final CreditService credit;
    private final CreditEventRepository creditEvents;
    private final Clock clock;

    @Transactional
    public PaymentAttempt reserve(Long orderId, Long actorId, PaymentAttempt.Channel channel) {
        Order order = orders.findLockedById(orderId).orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!Objects.equals(order.getPayerId(), actorId)) throw new BusinessException(403, "只有付款方可以发起支付");
        if (order.isDisputed() || order.getStatus() != Order.OrderStatus.completed) throw new BusinessException(409, "请先验收并处理待决异议");
        if (order.getAmountDue() == null || order.getAmountDue().signum() <= 0) throw new BusinessException(409, "无偿或历史待核对订单无需发起在线支付");
        if (channel == PaymentAttempt.Channel.WECHAT && order.getAmountDue().compareTo(new java.math.BigDecimal("21474836.47")) > 0)
            throw new BusinessException(400, "金额超过微信支付接口可表示的分值范围");
        if (order.getPaymentStatus() == Order.PaymentStatus.paid) throw new BusinessException(409, "订单已经付款，请勿重复支付");
        if (!properties.isManualSettlementEnabled()) throw new BusinessException(503, "尚未配置服务方结算方式，暂不能收款");
        var existing = attempts.findByOrderIdAndActiveSlot(orderId, 1);
        if (existing.isPresent()) {
            PaymentAttempt attempt = existing.get();
            if (attempt.getChannel() != channel) throw new BusinessException(409, "本订单已有另一渠道支付单，请继续使用原渠道，避免重复收款");
            return attempt;
        }
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setOrderId(orderId);
        attempt.setOutTradeNo(UUID.randomUUID().toString().replace("-", ""));
        attempt.setAmount(order.getAmountDue());
        attempt.setChannel(channel);
        attempt.setAppId(channel == PaymentAttempt.Channel.WECHAT ? properties.getWechat().getAppId() : properties.getAlipay().getAppId());
        attempt.setMerchantId(channel == PaymentAttempt.Channel.WECHAT ? properties.getWechat().getMerchantId() : properties.getAlipay().getSellerId());
        attempt.setCreatedAt(LocalDateTime.now(clock));
        return attempts.save(attempt);
    }

    @Transactional
    public PaymentAttempt saveCode(Long attemptId, String codeUrl) {
        PaymentAttempt initial = attempts.findById(attemptId).orElseThrow();
        orders.findLockedById(initial.getOrderId()).orElseThrow();
        PaymentAttempt attempt = attempts.findLockedById(attemptId).orElseThrow();
        if (codeUrl == null || codeUrl.isBlank() || codeUrl.length() > 1500) throw new BusinessException(502, "支付渠道未返回有效二维码");
        attempt.setCodeUrl(codeUrl);
        return attempt;
    }

    /** 通知、主动查询、后台补偿都走这个入口，不提供“前端告诉我已支付”的接口。 */
    @Transactional
    public void recordVerified(PaymentGateway.VerifiedPayment result) {
        if (!result.paid() && !result.closed()) return;
        PaymentAttempt initial = attempts.findByOutTradeNo(result.outTradeNo()).orElseThrow(() -> new BusinessException(404, "支付单不存在"));
        Order order = orders.findLockedById(initial.getOrderId()).orElseThrow();
        PaymentAttempt attempt = attempts.findLockedById(initial.getId()).orElseThrow();
        if (result.closed()) {
            if (attempt.getChannel() != result.channel() || !Objects.equals(attempt.getAppId(), result.appId())
                    || !Objects.equals(attempt.getMerchantId(), result.merchantId())) throw new BusinessException(400, "关单结果与本地商户不一致");
            if (attempt.getStatus() == PaymentAttempt.Status.PENDING) {
                attempt.setStatus(PaymentAttempt.Status.CLOSED);
                attempt.setClosedAt(LocalDateTime.now(clock));
                attempt.setActiveSlot(null);
            }
            return;
        }
        // 渠道在已确认关单后又报成功属于异常资金事件，记录入账并冻结人工核对，不能再扣用户一次。
        if (attempt.getStatus() == PaymentAttempt.Status.CLOSED) {
            order.setDisputed(true);
            order.setDisputeReason("已关闭历史支付单收到成功通知，待人工核对");
        }
        if (attempt.getChannel() != result.channel() || !Objects.equals(attempt.getAppId(), result.appId())
                || !Objects.equals(attempt.getMerchantId(), result.merchantId()) || !"CNY".equals(result.currency())
                || result.amount() == null || result.amount().compareTo(attempt.getAmount()) != 0
                || result.transactionId() == null || result.transactionId().isBlank() || result.paidAt() == null)
            throw new BusinessException(400, "支付通知与本地商户、金额或币种不一致");
        if (attempt.getStatus() == PaymentAttempt.Status.PAID) {
            if (!Objects.equals(attempt.getProviderTransactionId(), result.transactionId())) throw new BusinessException(409, "支付流水冲突，请人工核对");
            return;
        }
        attempt.setStatus(PaymentAttempt.Status.PAID);
        attempt.setProviderTransactionId(result.transactionId());
        attempt.setPaidAt(result.paidAt());
        order.setPaymentStatus(Order.PaymentStatus.paid);
        order.setPaidAt(result.paidAt());
        order.setSettlementStatus(Order.SettlementStatus.pending);
        order.setReviewEligibleAt(order.isDisputed() ? null : result.paidAt());
        order.setEscalationDueAt(null);
        order.setEscalatedAt(null);
        // 渠道证明付款发生在截止前，只是通知晚到：按原实际扣分额撤销误罚，避免0分边界返多。
        if (order.getOverdueAt() != null && order.getPaymentDueAt() != null && !result.paidAt().isAfter(order.getPaymentDueAt())) {
            creditEvents.findByEventKey("overdue:" + order.getId()).ifPresent(event -> credit.adjust(order.getPayerId(),
                    event.getPreviousScore() - event.getResultingScore(), "overdue-reversal:" + order.getId(), "渠道核实付款未逾期，撤销通知延迟误罚"));
        }
        notifications.createNotification(order.getPayerId(), "支付已确认", "订单#" + order.getId() + "付款已确认，请勿重复付款。", "PAYMENT");
        notifications.createNotification(order.getPayeeId(), "订单已付款，待结算", "平台已确认收款，管理员实际转账后会登记人工结算凭证。", "SETTLEMENT");
    }

    @Transactional
    public void markQueried(Long id) {
        attempts.findById(id).ifPresent(p -> p.setLastQueriedAt(LocalDateTime.now(clock)));
    }
}
