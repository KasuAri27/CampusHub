package com.campushub.order;

import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.order.Order.OrderStatus;
import com.campushub.task.ExpressTask;
import com.campushub.task.Task;
import com.campushub.task.Task.TaskStatus;
import com.campushub.task.TaskRepository;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void acceptTaskCreatesOrderAndMovesTaskToInProgress() {
        User requester = user(1L, User.Role.requester);
        User provider = user(2L, User.Role.provider);
        Task task = task(10L, requester, TaskStatus.published, new BigDecimal("12.50"));

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(99L);
            return order;
        });

        OrderDTO dto = orderService.acceptTask(10L, 2L);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getTask()).isSameAs(task);
        assertThat(saved.getRequester()).isSameAs(requester);
        assertThat(saved.getProvider()).isSameAs(provider);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.in_progress);
        assertThat(saved.getPaymentCode()).isEqualTo("pay-code.png");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.in_progress);
        assertThat(dto.getOrderId()).isEqualTo(99L);
        assertThat(dto.getOrderStatus()).isEqualTo("in_progress");
        assertThat(dto.getTaskStatus()).isEqualTo("in_progress");
    }

    @Test
    void acceptTaskRejectsRequesterAcceptingOwnTask() {
        User requester = user(1L, User.Role.requester);
        Task task = task(10L, requester, TaskStatus.published, BigDecimal.ZERO);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> orderService.acceptTask(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能接自己发布的需求");

        verify(userRepository, never()).findById(1L);
        verify(orderRepository, never()).save(any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.published);
    }

    @Test
    void acceptTaskRequiresPaymentCodeForPaidTask() {
        User requester = user(1L, User.Role.requester);
        Task task = task(10L, requester, TaskStatus.published, new BigDecimal("8.00"));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> orderService.acceptTask(10L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("有偿需求必须上传收款码图片");

        verify(orderRepository, never()).save(any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.published);
    }

    @Test
    void completeOrderAllowsRequesterAndCompletesTask() {
        User requester = user(1L, User.Role.requester);
        User provider = user(2L, User.Role.provider);
        Task task = task(10L, requester, TaskStatus.in_progress, BigDecimal.ZERO);
        Order order = order(20L, task, requester, provider, OrderStatus.in_progress);
        when(orderRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(order));

        orderService.completeOrder(20L, 1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.completed);
        assertThat(order.getCompletedAt()).isNotNull();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.completed);
    }

    @Test
    void cancelOrderByProviderReopensTaskForOtherProviders() {
        User requester = user(1L, User.Role.requester);
        User provider = user(2L, User.Role.provider);
        Task task = task(10L, requester, TaskStatus.in_progress, BigDecimal.ZERO);
        Order order = order(20L, task, requester, provider, OrderStatus.in_progress);
        when(orderRepository.findByIdWithDetails(20L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(20L, 2L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.cancelled);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.published);
    }

    @Test
    void getAcceptHistoryRejectsUnknownStatus() {
        assertThatThrownBy(() -> orderService.getAcceptHistory(2L, "unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态参数不合法");
        verify(orderRepository, never()).findAcceptHistory(any(), any());
    }

    private static User user(Long id, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setName("用户" + id);
        user.setCreditScore(100);
        return user;
    }

    private static Task task(Long id, User requester, TaskStatus status, BigDecimal reward) {
        ExpressTask task = new ExpressTask();
        task.setId(id);
        task.setRequester(requester);
        task.setTitle("帮取快递");
        task.setDescription("请帮忙从驿站取快递");
        task.setReward(reward);
        task.setStatus(status);
        return task;
    }

    private static Order order(Long id, Task task, User requester, User provider, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setTask(task);
        order.setRequester(requester);
        order.setProvider(provider);
        order.setStatus(status);
        return order;
    }
}
