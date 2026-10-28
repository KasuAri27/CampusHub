package com.campushub.order;

import com.campushub.chat.service.ChatIntegrationService;
import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.order.Order.OrderStatus;
import com.campushub.task.SecondHandTask;
import com.campushub.task.Task;
import com.campushub.task.Task.TaskStatus;
import com.campushub.task.TaskRepository;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final ChatIntegrationService chatIntegrationService;

    @Transactional(readOnly = true)
    public OrderDTO getByTaskId(Long taskId) {
        Order order = orderRepository.findFirstByTaskIdOrderByIdDesc(taskId)
                .orElseThrow(() -> new BusinessException(404, "找不到对应的订单"));
        return toDTO(order);
    }

    @Transactional
    public OrderDTO acceptTask(Long taskId, Long currentUserId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "需求不存在"));

        if (task.getStatus() != TaskStatus.published) {
            throw new BusinessException(400, "该需求不可接单");
        }
        if (task.getRequester().getId().equals(currentUserId)) {
            throw new BusinessException(400, "不能接自己发布的需求");
        }

        User provider = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (provider.getCreditScore() < 70) {
            throw new BusinessException(403, "您的信用分低于70分，暂无接单权限！");
        }

        Order order = Order.builder()
                .task(task)
                .requester(task.getRequester())
                .provider(provider)
                .status(OrderStatus.in_progress)
                .build();

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
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));

        if (!order.getRequester().getId().equals(currentUserId)) {
            throw new BusinessException(403, "只有需求方可以确认完成订单");
        }
        if (order.getStatus() != OrderStatus.accepted && order.getStatus() != OrderStatus.in_progress) {
            throw new BusinessException(400, "当前订单状态无法确认完成");
        }

        order.setStatus(OrderStatus.completed);
        order.setCompletedAt(LocalDateTime.now());
        order.getTask().setStatus(TaskStatus.completed);

        String msg = order.getRequester().getName() + " 已确认该需求被完成";
        if (order.getTask().getReward() != null && order.getTask().getReward().compareTo(BigDecimal.ZERO) > 0) {
            msg += "，请注意查看报酬是否已支付";
        }
        notificationService.createNotification(order.getProvider().getId(), "订单已完成", msg, "ORDER_COMPLETED");
        chatIntegrationService.handleTaskCompleted(order.getTask());
    }

    @Transactional
    public void cancelOrder(Long orderId, Long currentUserId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));

        boolean isRequester = order.getRequester().getId().equals(currentUserId);
        boolean isProvider = order.getProvider().getId().equals(currentUserId);
        if (!isRequester && !isProvider) {
            throw new BusinessException(403, "无权操作此订单");
        }
        if (order.getStatus() != OrderStatus.accepted && order.getStatus() != OrderStatus.in_progress) {
            throw new BusinessException(400, "当前订单状态无法取消");
        }

        order.setStatus(OrderStatus.cancelled);
        order.setCancelledAt(LocalDateTime.now());
        order.getTask().setStatus(isProvider ? TaskStatus.published : TaskStatus.cancelled);
        chatIntegrationService.handleCancelOrder(order.getTask(), currentUserId, isRequester);
    }

    @Transactional(readOnly = true)
    public List<OrderDTO> getAcceptHistory(Long userId, String status) {
        OrderStatus orderStatus = parseStatus(status);
        return orderRepository.findAcceptHistory(userId, orderStatus).stream()
                .map(this::toDTO)
                .toList();
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

    private OrderDTO toDTO(Order order) {
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
                .paymentCode(order.getPaymentCode())
                .build();
    }
}
