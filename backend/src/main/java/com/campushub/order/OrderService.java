package com.campushub.order;

import com.campushub.chat.service.ChatIntegrationService;
import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.order.Order.OrderStatus;
import com.campushub.task.SecondHandTask;
import com.campushub.task.Task;
import com.campushub.task.Task.TaskStatus;
import com.campushub.task.TaskRepository;
import com.campushub.task.TeamingTask;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final ChatIntegrationService chatIntegrationService;
    private final OrderLifecycleService lifecycle;
    private final Clock clock;
    private final OrderLocks locks;

    @Transactional(readOnly = true)
    public OrderDTO getByTaskId(Long taskId, Long actorId) {
        // 返回当前参与者自己的订单，组队不能拿“最新一单”冒充所有成员的订单。
        Order order = orderRepository.findByTaskId(taskId).stream()
                .filter(o -> o.getRequester().getId().equals(actorId) || o.getProvider().getId().equals(actorId))
                .max(java.util.Comparator.comparing(Order::getId))
                .orElseThrow(() -> new BusinessException(404, "找不到对应的订单"));
        return toDTO(order);
    }

    @Transactional
    public OrderDTO acceptTask(Long taskId, Long currentUserId) {
        Task task = taskRepository.findLockedById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));

        if (task.getStatus() != TaskStatus.published) {
            throw new BusinessException(400, "该需求不可接单");
        }
        if (task.getDeadline() != null && !task.getDeadline().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(409, "需求已过期，无法接单");
        }
        if (task.getRequester().getId().equals(currentUserId)) {
            throw new BusinessException(400, "不能接自己发布的需求");
        }

        User provider = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (provider.getStatus() != 1) throw new BusinessException(403, "账号已被禁用");
        if (provider.getCreditScore() < 70) {
            throw new BusinessException(403, "您的信用分低于70分，暂无接单权限！");
        }

        if (orderRepository.existsByTask_IdAndProvider_IdAndStatusNot(taskId, currentUserId, OrderStatus.cancelled))
            throw new BusinessException(409, "您已参与该任务");
        BigDecimal amount = task instanceof TeamingTask || task.getReward() == null ? BigDecimal.ZERO : task.getReward();
        // 旧报价缺少新业务所需字段时不能擅自按一小时或零元收费，要求重新明确报价。
        if (task instanceof com.campushub.task.TutoringTask tutoring && tutoring.getDurationHours() == null)
            throw new BusinessException(409, "旧辅导需求缺少约定时长，请重新发布后接单");
        if (task instanceof SecondHandTask secondHand && secondHand.getItemPrice() == null)
            throw new BusinessException(409, "二手需求缺少约定总价，请重新发布后接单");
        try { amount = amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new BusinessException(400, "金额最多保留两位小数"); }
        if (amount.signum() < 0) throw new BusinessException(400, "报酬不能为负数");
        boolean selling = task instanceof SecondHandTask secondHand && "SELL".equals(secondHand.getTradeMode());

        Order order = Order.builder()
                .task(task)
                .requester(task.getRequester())
                .provider(provider)
                .status(OrderStatus.in_progress)
                .acceptedAt(LocalDateTime.now(clock))
                .amountDue(amount)
                .payerId(selling ? currentUserId : task.getRequester().getId())
                .payeeId(selling ? task.getRequester().getId() : currentUserId)
                .paymentStatus(amount.signum() == 0 ? Order.PaymentStatus.not_required : Order.PaymentStatus.unpaid)
                .settlementStatus(amount.signum() == 0 ? Order.SettlementStatus.not_required : Order.SettlementStatus.pending)
                .build();

        // 组队金额固定为零；人数上限沿用现有群聊定义，包含发起人。
        if (!(task instanceof TeamingTask team) || orderRepository.countByTask_IdAndStatusNot(taskId, OrderStatus.cancelled) + 2 >= team.getMaxMembers())
            task.setStatus(TaskStatus.in_progress);
        order = orderRepository.save(order);

        notificationService.createNotification(
                task.getRequester().getId(),
                "有人接单了",
                provider.getName() + " 接了你的需求《" + task.getTitle() + "》。",
                "ORDER_ACCEPTED"
        );
        chatIntegrationService.handleAfterAccept(task, order.getId(), currentUserId);
        return toDTO(order);
    }

    @Transactional
    public void completeOrder(Long orderId, Long currentUserId) {
        // 保留原 HTTP 路径，但语义明确为验收；支付及人工结算由各自入口完成。
        lifecycle.acceptCompletion(orderId, currentUserId);
    }

    @Transactional
    public void cancelOrder(Long orderId, Long currentUserId) {
        Order order = locks.lock(orderId);

        boolean isRequester = order.getRequester().getId().equals(currentUserId);
        boolean isProvider = order.getProvider().getId().equals(currentUserId);
        if (!isRequester && !isProvider) {
            throw new BusinessException(403, "无权操作此订单");
        }
        if (order.getStatus() != OrderStatus.in_progress || order.isDisputed()) {
            throw new BusinessException(400, "当前订单状态无法取消");
        }
        if (isRequester && order.getTask() instanceof TeamingTask) {
            List<Order> members = orderRepository.findByTaskId(order.getTask().getId());
            if (members.stream().anyMatch(member -> member.getStatus() == OrderStatus.awaiting_acceptance || member.isDisputed()))
                throw new BusinessException(409, "已有待验收或争议成员，请先处理后再结束组队");
            for (Order member : members) if (member.getStatus() == OrderStatus.in_progress) {
                member.setStatus(OrderStatus.cancelled); member.setCancelledAt(LocalDateTime.now(clock));
            }
        }

        order.setStatus(OrderStatus.cancelled);
        order.setCancelledAt(LocalDateTime.now(clock));
        boolean canReopen = order.getTask().getDeadline() == null || order.getTask().getDeadline().isAfter(LocalDateTime.now(clock));
        order.getTask().setStatus(isProvider && canReopen && order.getTask().getStatus() != TaskStatus.cancelled ? TaskStatus.published : TaskStatus.cancelled);
        chatIntegrationService.handleCancelOrder(order.getTask(), currentUserId, isRequester);
    }

    @Transactional(readOnly = true)
    public List<OrderDTO> getAcceptHistory(Long userId, String status) {
        OrderStatus orderStatus = parseStatus(status);
        return orderRepository.findAcceptHistory(userId, orderStatus).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDTO> getMyOrders(Long actorId) {
        return orderRepository.findByRequester_IdOrProvider_IdOrderByIdDesc(actorId, actorId).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public OrderDTO getDetail(Long orderId, Long actorId) {
        Order order = orderRepository.findByIdWithDetails(orderId).orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.getRequester().getId().equals(actorId) && !order.getProvider().getId().equals(actorId))
            throw new BusinessException(403, "无权读取此订单");
        return toDTO(order);
    }

    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "订单状态参数不合法");
        }
    }

    public OrderDTO toDTO(Order order) {
        Task task = order.getTask();
        return OrderDTO.builder()
                .orderId(order.getId())
                .orderStatus(order.getStatus().name())
                .acceptedAt(order.getAcceptedAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .taskId(task.getId())
                .taskStatus(task.getStatus().name())
                .title(task.getTitle())
                .description(task.getDescription())
                .reward(task.getReward())
                .requesterId(order.getRequester().getId())
                .providerId(order.getProvider().getId())
                .amountDue(order.getAmountDue()).payerId(order.getPayerId()).payeeId(order.getPayeeId())
                .paymentStatus(order.getPaymentStatus() == null ? null : order.getPaymentStatus().name())
                .settlementStatus(order.getSettlementStatus() == null ? null : order.getSettlementStatus().name())
                .completionSubmittedAt(order.getCompletionSubmittedAt()).acceptanceDueAt(order.getAcceptanceDueAt())
                .paymentDueAt(order.getPaymentDueAt()).escalationDueAt(order.getEscalationDueAt()).escalatedAt(order.getEscalatedAt())
                .paidAt(order.getPaidAt()).reviewEligibleAt(order.getReviewEligibleAt()).settledAt(order.getSettledAt())
                .disputed(order.isDisputed())
                .disputeReason(order.getDisputeReason())
                .hasReview(task.getCategory() != null && Boolean.TRUE.equals(task.getCategory().getHasReview()))
                .taskType(task instanceof TeamingTask ? "TEAM" : task instanceof SecondHandTask ? "FLEA" : "SERVICE")
                .build();
    }
}
