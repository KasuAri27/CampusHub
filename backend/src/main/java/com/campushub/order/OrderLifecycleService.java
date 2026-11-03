package com.campushub.order;

import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.task.Task;
import com.campushub.user.CreditService;
import com.campushub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 已确认的 24h 验收、1h 付款、一次扣10分及再24h升级规则。所有时间判断可重复执行。 */
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {
    private final OrderRepository orders;
    private final UserRepository users;
    private final CreditService credit;
    private final NotificationService notifications;
    private final Clock clock;
    private final com.campushub.payment.PaymentAttemptRepository paymentAttempts;
    private final com.campushub.complaint.ComplaintRepository complaints;
    private final OrderLocks locks;

    @Transactional
    public void submitCompletion(Long orderId, Long actorId) {
        Order order = locked(orderId);
        requireActor(order.getPayeeId(), actorId, "只有服务提供方可以提交完成");
        if (order.isDisputed()) throw new BusinessException(409, "订单正在争议处理中");
        if (order.getStatus() == Order.OrderStatus.awaiting_acceptance) return;
        if (order.getStatus() != Order.OrderStatus.in_progress) throw new BusinessException(409, "订单状态无法提交完成");
        LocalDateTime now = LocalDateTime.now(clock);
        order.setCompletionSubmittedAt(now);
        order.setAcceptanceDueAt(now.plusHours(24));
        order.setStatus(Order.OrderStatus.awaiting_acceptance);
        notifications.createNotification(order.getPayerId(), "请验收服务", "服务方已提交完成，24小时无异议将自动验收。", "ACCEPTANCE");
    }

    @Transactional
    public void acceptCompletion(Long orderId, Long actorId) {
        Order order = locked(orderId);
        requireActor(order.getPayerId(), actorId, "只有需求验收方可以确认完成");
        if (order.isDisputed()) throw new BusinessException(409, "请等待争议裁决");
        if (order.getStatus() == Order.OrderStatus.completed) return;
        if (order.getStatus() != Order.OrderStatus.awaiting_acceptance) throw new BusinessException(409, "请先由服务方提交完成");
        accept(order, LocalDateTime.now(clock));
    }

    /** 支付迟到、验收扫描延迟时，截止仍按业务事件时点推算，不按扫描执行时刻延长。 */
    private void accept(Order order, LocalDateTime acceptedAt) {
        order.setStatus(Order.OrderStatus.completed);
        order.setCompletedAt(acceptedAt);
        if (!(order.getTask() instanceof com.campushub.task.TeamingTask)
                || orders.findByTaskId(order.getTask().getId()).stream().allMatch(o -> o.getStatus() == Order.OrderStatus.completed || o.getStatus() == Order.OrderStatus.cancelled))
            order.getTask().setStatus(Task.TaskStatus.completed);
        if (order.getAmountDue().signum() == 0) {
            order.setReviewEligibleAt(acceptedAt);
        } else {
            order.setPaymentDueAt(acceptedAt.plusHours(1));
        }
        notifications.createNotification(order.getPayerId(), "验收已完成", "有偿订单请在验收后1小时内付款；无偿订单可直接评价。", "ACCEPTED");
        notifications.createNotification(order.getPayeeId(), "服务已验收", "验收已完成，付款及结算状态请在订单中查看。", "ACCEPTED");
    }

    @Transactional
    public void openDispute(Long orderId, Long actorId, String reason) {
        Order order = locked(orderId);
        if (!actorId.equals(order.getRequester().getId()) && !actorId.equals(order.getProvider().getId()))
            throw new BusinessException(403, "只有订单参与者可以提出异议");
        if (order.getStatus() == Order.OrderStatus.cancelled) throw new BusinessException(409, "已取消订单无法提出服务异议");
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new BusinessException(400, "请填写500字以内的异议说明");
        if (order.isDisputed()) return;
        order.setDisputed(true);
        order.setDisputedAt(LocalDateTime.now(clock));
        order.setDisputeReason(reason);
        notifyAdmins(order, "订单提出正式异议，请裁决服务结果及付款义务。");
    }

    /** 每笔订单独立事务；行锁保证多实例扫描时最多扣一次10分。 */
    @Transactional
    public void processDeadline(Long orderId) {
        Order order = locks.lock(orderId);
        if (order.getAmountDue() == null || order.isDisputed() || order.getStatus() == Order.OrderStatus.cancelled) return;
        LocalDateTime now = LocalDateTime.now(clock);
        if (order.getStatus() == Order.OrderStatus.awaiting_acceptance && !order.getAcceptanceDueAt().isAfter(now)) {
            accept(order, order.getAcceptanceDueAt());
        }
        if (order.getPaymentStatus() != Order.PaymentStatus.unpaid || order.getPaymentDueAt() == null) return;
        if (!order.getPaymentDueAt().isAfter(now) && order.getOverdueAt() == null) {
            credit.adjust(order.getPayerId(), -10, "overdue:" + orderId, "验收后1小时未付款");
            order.setOverdueAt(order.getPaymentDueAt());
            order.setEscalationDueAt(order.getPaymentDueAt().plusHours(24));
            notifications.createNotification(order.getPayerId(), "付款逾期扣分", "本订单已扣10分；24小时后仍未付款将由管理员介入。", "OVERDUE");
        }
        if (order.getEscalationDueAt() != null && !order.getEscalationDueAt().isAfter(now) && order.getEscalatedAt() == null) {
            order.setEscalatedAt(now);
            notifyAdmins(order, "付款逾期宽限期已结束，请根据金额及证据决定额外扣分或封禁。");
        }
    }

    /** 裁决由管理员给出明确金额、付款期限和可审计理由，不把异议自动视为成立。 */
    @Transactional
    public void resolveDispute(Long orderId, Long adminId, java.math.BigDecimal amount, int paymentHours, String reason) {
        requireAdmin(adminId);
        Order order = locked(orderId);
        if (!order.isDisputed()) throw new BusinessException(409, "订单没有待处理异议");
        if (amount == null || amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(order.getAmountDue()) > 0
                || paymentHours < 1 || paymentHours > 168 || reason == null || reason.isBlank() || reason.length() > 500)
            throw new BusinessException(400, "裁决金额、付款期限或理由不合法");
        // 已发支付二维码可能仍可付款；没有关闭渠道原单之前，不能擅自重写金额。
        if (amount.compareTo(order.getAmountDue()) != 0 && paymentAttempts.existsByOrderIdAndStatusIn(orderId,
                java.util.List.of(com.campushub.payment.PaymentAttempt.Status.PENDING, com.campushub.payment.PaymentAttempt.Status.PAID)))
            throw new BusinessException(409, "已签发支付单；请先完成渠道关单或退款核对，不能直接变更金额");
        if (order.getPaymentStatus() == Order.PaymentStatus.paid) {
            order.setDisputed(false);
            order.setReviewEligibleAt(LocalDateTime.now(clock));
            resolveComplaints(orderId, adminId, reason);
            return;
        }
        order.setAmountDue(amount);
        order.setDisputed(false);
        order.setDisputeReason("管理员裁决：" + reason);
        order.setStatus(Order.OrderStatus.completed);
        order.setCompletedAt(LocalDateTime.now(clock));
        order.setPaymentStatus(amount.signum() == 0 ? Order.PaymentStatus.not_required : Order.PaymentStatus.unpaid);
        order.setSettlementStatus(amount.signum() == 0 ? Order.SettlementStatus.not_required : Order.SettlementStatus.pending);
        order.setPaymentDueAt(amount.signum() == 0 ? null : LocalDateTime.now(clock).plusHours(paymentHours));
        order.setReviewEligibleAt(amount.signum() == 0 ? LocalDateTime.now(clock) : null);
        order.setEscalationDueAt(order.getOverdueAt() == null || amount.signum() == 0 ? null : order.getPaymentDueAt().plusHours(24));
        order.setEscalatedAt(null);
        order.getTask().setStatus(Task.TaskStatus.completed);
        resolveComplaints(orderId, adminId, reason);
        notifications.createNotification(order.getPayerId(), "争议裁决结果", reason + "；应付金额：" + amount, "DISPUTE_RESOLVED");
        notifications.createNotification(order.getPayeeId(), "争议裁决结果", reason + "；应付金额：" + amount, "DISPUTE_RESOLVED");
    }

    @Transactional
    public void penalizeOverdue(Long orderId, Long adminId, String operationId, int deduction, boolean ban, String reason) {
        requireAdmin(adminId);
        Order order = locked(orderId);
        if (order.isDisputed() || order.getEscalatedAt() == null || order.getPaymentStatus() != Order.PaymentStatus.unpaid)
            throw new BusinessException(409, "只有已升级且未支付、无争议订单可以处罚");
        if (deduction < 0 || deduction > 100 || operationId == null || !operationId.matches("[a-zA-Z0-9-]{8,64}")
                || reason == null || reason.isBlank() || reason.length() > 500 || deduction == 0 && !ban)
            throw new BusinessException(400, "请提供合法处罚、操作编号及理由");
        boolean applied = credit.adjust(order.getPayerId(), -deduction, "admin:" + orderId + ":" + operationId, "订单" + orderId + "：" + reason + (ban ? "；封禁" : ""));
        if (applied && ban) users.findLockedById(order.getPayerId()).orElseThrow().setStatus(0);
        if (applied) notifications.createNotification(order.getPayerId(), "管理员处罚通知", reason + "；扣分：" + deduction + (ban ? "；账号已封禁" : ""), "ADMIN_PENALTY");
    }

    public void requireAdmin(Long actorId) {
        if (actorId == null || users.findById(actorId).filter(u -> u.getRole() == com.campushub.user.User.Role.admin && u.getStatus() == 1).isEmpty())
            throw new BusinessException(403, "无管理员权限");
    }

    private Order locked(Long id) {
        Order order = locks.lock(id);
        if (order.getAmountDue() == null) throw new BusinessException(409, "历史订单须先人工核对，不能自动应用新支付或处罚规则");
        return order;
    }
    private void requireActor(Long expected, Long actual, String message) {
        if (expected == null || !expected.equals(actual)) throw new BusinessException(403, message);
    }
    private void notifyAdmins(Order order, String message) {
        users.findAll().stream().filter(u -> u.getRole() == com.campushub.user.User.Role.admin).forEach(u ->
                notifications.createNotification(u.getId(), "订单需人工处理", "订单#" + order.getId() + "；金额：" + order.getAmountDue() + "；" + message, "ADMIN_ACTION"));
    }
    private void resolveComplaints(Long orderId, Long administratorId, String reason) {
        complaints.findByOrderId(orderId).stream().filter(c -> !c.isResolved()).forEach(c -> {
            c.setResolved(true);
            c.setResolvedBy(administratorId);
            c.setResolvedAt(LocalDateTime.now(clock));
            c.setResolution(reason);
        });
    }
}
