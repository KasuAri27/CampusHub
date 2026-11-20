package com.campushub.task;

import com.campushub.category.Category;
import com.campushub.category.CategoryRepository;
import com.campushub.common.BusinessException;
import com.campushub.common.PageResult;
import com.campushub.task.Task.TaskStatus;
import com.campushub.user.User;
import com.campushub.user.User.Role;
import com.campushub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;
    @Mock
    private com.campushub.notification.NotificationService notificationService;
    @Mock
    private com.campushub.chat.service.ChatIntegrationService chatIntegrationService;
    @Mock
    private com.campushub.order.OrderRepository orderRepository;
    @Mock
    private com.campushub.review.ReviewRepository reviewRepository;
    @Mock
    private com.campushub.complaint.ComplaintRepository complaintRepository;
    @Mock
    private com.campushub.payment.PaymentAttemptRepository paymentAttempts;

    @InjectMocks
    private TaskService taskService;

    /** 固定业务时钟：2026-11-16 09:30（Asia/Shanghai），用于断言 publishedAt 不依赖 JVM 时区。 */
    private static final java.time.Clock FIXED_CLOCK = java.time.Clock.fixed(
            java.time.Instant.parse("2026-11-16T01:30:00Z"), java.time.ZoneId.of("Asia/Shanghai"));

    @org.junit.jupiter.api.BeforeEach
    void injectClock() {
        // Clock 是抽象类型，构造器注入拿不到 Mockito mock，这里显式注入固定时钟。
        org.springframework.test.util.ReflectionTestUtils.setField(taskService, "clock", FIXED_CLOCK);
    }

    @Test
    void approveByAdminLocksTaskPublishesAtBusinessTimeAndNotifies() {
        User requester = user(1L, Role.user);
        Task task = task(10L, requester, TaskStatus.pending_review, category(3, true));
        when(taskRepository.findLockedById(10L)).thenReturn(Optional.of(task));

        Task result = taskService.approveByAdmin(10L);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.published);
        assertThat(result.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 11, 16, 9, 30));
        verify(taskRepository).save(task);
        verify(chatIntegrationService, never()).createTeamGroup(any());
        verify(notificationService).createNotification(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("需求审核结果"),
                org.mockito.ArgumentMatchers.contains("已通过审核"), org.mockito.ArgumentMatchers.eq("TASK_REVIEW_RESULT"));
    }

    @Test
    void approveByAdminCreatesTeamGroupForTeamingTask() {
        User requester = user(1L, Role.user);
        TeamingTask team = new TeamingTask();
        team.setId(11L);
        team.setRequester(requester);
        team.setTitle("组队参加比赛");
        team.setStatus(TaskStatus.pending_review);
        when(taskRepository.findLockedById(11L)).thenReturn(Optional.of(team));

        taskService.approveByAdmin(11L);

        verify(chatIntegrationService).createTeamGroup(team);
    }

    @Test
    void approveByAdminRejectsTaskThatIsNoLongerPendingReview() {
        // 第二名管理员在第一名之后拿到锁，读到 published，必须被拒绝而不是重复建群/通知。
        Task task = task(10L, user(1L, Role.user), TaskStatus.published, category(3, true));
        when(taskRepository.findLockedById(10L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.approveByAdmin(10L))
                .isInstanceOf(BusinessException.class).hasMessage("仅待审核状态的需求可以审核通过");

        verify(taskRepository, never()).save(any());
        verify(chatIntegrationService, never()).createTeamGroup(any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any());
    }

    @Test
    void approveByAdminReturns404WhenTaskMissing() {
        when(taskRepository.findLockedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.approveByAdmin(99L))
                .isInstanceOf(BusinessException.class).hasMessage("需求不存在");
    }

    @Test
    void rejectByAdminCancelsAndAppendsReasonToNotification() {
        Task task = task(10L, user(1L, Role.user), TaskStatus.pending_review, category(3, true));
        when(taskRepository.findLockedById(10L)).thenReturn(Optional.of(task));

        taskService.rejectByAdmin(10L, "描述不清");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.cancelled);
        verify(notificationService).createNotification(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("需求审核结果"),
                org.mockito.ArgumentMatchers.argThat(c -> c.contains("已被驳回") && c.contains("驳回理由：描述不清")),
                org.mockito.ArgumentMatchers.eq("TASK_REVIEW_RESULT"));
    }

    @Test
    void rejectByAdminRefusesAlreadyApprovedTask() {
        // 已通过的需求不能被后到的驳回请求覆盖成 cancelled（丢失更新）。
        Task task = task(10L, user(1L, Role.user), TaskStatus.published, category(3, true));
        when(taskRepository.findLockedById(10L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.rejectByAdmin(10L, "x"))
                .isInstanceOf(BusinessException.class).hasMessage("仅待审核状态的需求可以驳回");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.published);
        verify(notificationService, never()).createNotification(any(), any(), any(), any());
    }

    @Test
    void deleteByAdminLocksTaskBeforeReadingOrdersThenCascades() {
        Task task = task(10L, user(1L, Role.user), TaskStatus.cancelled, category(3, true));
        com.campushub.order.Order legacy = legacyOrder(20L);
        when(taskRepository.findLockedById(10L)).thenReturn(Optional.of(task));
        when(orderRepository.findByTaskId(10L)).thenReturn(List.of(legacy));
        when(complaintRepository.findByOrderId(20L)).thenReturn(List.of());
        when(paymentAttempts.existsByOrderId(20L)).thenReturn(false);

        taskService.deleteByAdmin(10L);

        var inOrder = org.mockito.Mockito.inOrder(taskRepository, orderRepository, reviewRepository);
        inOrder.verify(taskRepository).findLockedById(10L);
        inOrder.verify(orderRepository).findByTaskId(10L);
        inOrder.verify(reviewRepository).deleteByOrder_Id(20L);
        inOrder.verify(orderRepository).delete(legacy);
        inOrder.verify(taskRepository).deleteById(10L);
    }

    @Test
    void deleteByAdminKeepsOrdersWithAmountSnapshot() {
        Task task = task(10L, user(1L, Role.user), TaskStatus.in_progress, category(3, true));
        com.campushub.order.Order priced = legacyOrder(20L);
        priced.setAmountDue(new BigDecimal("6.50"));
        when(taskRepository.findLockedById(10L)).thenReturn(Optional.of(task));
        when(orderRepository.findByTaskId(10L)).thenReturn(List.of(priced));

        assertThatThrownBy(() -> taskService.deleteByAdmin(10L))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(409);

        verify(orderRepository, never()).delete(any(com.campushub.order.Order.class));
        verify(taskRepository, never()).deleteById(any());
    }

    @Test
    void deleteByAdminReturns409InsteadOfForeignKeyErrorWhenLegacyOrderHasComplaint() {
        Task task = task(10L, user(1L, Role.user), TaskStatus.cancelled, category(3, true));
        com.campushub.order.Order legacy = legacyOrder(20L);
        when(taskRepository.findLockedById(10L)).thenReturn(Optional.of(task));
        when(orderRepository.findByTaskId(10L)).thenReturn(List.of(legacy));
        when(complaintRepository.findByOrderId(20L)).thenReturn(List.of(new com.campushub.complaint.Complaint()));

        assertThatThrownBy(() -> taskService.deleteByAdmin(10L))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(409);

        verify(reviewRepository, never()).deleteByOrder_Id(any());
        verify(taskRepository, never()).deleteById(any());
    }

    @Test
    void deleteByAdminReturns404WhenTaskMissing() {
        when(taskRepository.findLockedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteByAdmin(99L))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(404);
    }

    @Test
    void pickupCodeIsVisibleOnlyToRequesterAndOrderParticipants() {
        User requester = user(1L, Role.user);
        ExpressTask task = (ExpressTask) task(10L, requester, TaskStatus.in_progress, category(3, true));
        task.setPickupCode("A-1234");
        when(taskRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(task));
        when(orderRepository.existsByTask_IdAndProvider_IdAndStatusNot(10L, 2L, com.campushub.order.Order.OrderStatus.cancelled)).thenReturn(true);
        when(orderRepository.existsByTask_IdAndProvider_IdAndStatusNot(10L, 3L, com.campushub.order.Order.OrderStatus.cancelled)).thenReturn(false);

        assertThat(taskService.getTaskDetail(10L, 1L).getPickupCode()).isEqualTo("A-1234");   // 发布者
        assertThat(taskService.getTaskDetail(10L, 2L).getPickupCode()).isEqualTo("A-1234");   // 订单参与者
        assertThat(taskService.getTaskDetail(10L, 3L).getPickupCode()).isNull();              // 无关登录用户
        assertThat(taskService.getTaskDetail(10L, null).getPickupCode()).isNull();            // 未登录
        // 其余字段不受裁剪影响。
        assertThat(taskService.getTaskDetail(10L, null).getPickupLocation()).isEqualTo("校门口驿站");
    }

    @Test
    void listHidesPickupCodeFromEveryoneButRequesterWithoutExtraQueries() {
        User requester = user(1L, Role.user);
        ExpressTask task = (ExpressTask) task(10L, requester, TaskStatus.published, category(3, true));
        task.setPickupCode("A-1234");
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(task), inv.getArgument(1), 1));
        // 列表页不查订单参与者，避免 N+1；匿名访问时 UserContext 为空。
        PageResult<TaskDTO> anonymous = taskService.listTasks(null, null, null, "latest", null, 1, 10);

        assertThat(anonymous.getItems().get(0).getPickupCode()).isNull();
        verify(orderRepository, never()).existsByTask_IdAndProvider_IdAndStatusNot(any(), any(), any());
    }

    /** 无金额快照的历史订单（amountDue 为空），管理员清理需求时允许级联删除。 */
    private static com.campushub.order.Order legacyOrder(Long id) {
        com.campushub.order.Order order = new com.campushub.order.Order();
        order.setId(id);
        return order;
    }

    @Test
    void createTaskAllowsRequesterAndReturnsDto() {
        User requester = user(1L, Role.user);
        Category category = category(3, true);
        TaskCreateRequest request = createRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(categoryRepository.findById(3)).thenReturn(Optional.of(category));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(10L);
            return task;
        });

        TaskDTO dto = taskService.createTask(request, 1L);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();
        assertThat(saved.getRequester()).isSameAs(requester);
        assertThat(saved.getCategory()).isSameAs(category);
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.pending_review);
        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getCategoryId()).isEqualTo(3);
        assertThat(dto.getHasReview()).isTrue();
        assertThat(dto.getPublisher().getUserId()).isEqualTo(1L);
    }

    @Test
    void createTaskRejectsAdministrator() {
        User admin = user(2L, Role.admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> taskService.createTask(createRequest(), 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("管理员不能发布需求");

        verify(categoryRepository, never()).findById(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void deleteOwnTaskAllowsOnlyOwnerAndPublishedTask() {
        User requester = user(1L, Role.user);
        Task task = task(10L, requester, TaskStatus.published, category(3, true));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        taskService.deleteOwnTask(1L, 10L);

        verify(taskRepository).delete(task);
    }

    @Test
    void deleteOwnTaskRejectsNonOwner() {
        User requester = user(1L, Role.user);
        Task task = task(10L, requester, TaskStatus.published, category(3, true));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.deleteOwnTask(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能删除自己发布的需求");

        verify(taskRepository, never()).delete((Task) any());
    }

    @Test
    void listTasksUsesOneBasedPaginationAndRewardSort() {
        User requester = user(1L, Role.user);
        Task task = task(10L, requester, TaskStatus.published, category(3, true));
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    assertThat(pageable.getPageNumber()).isEqualTo(1);
                    assertThat(pageable.getPageSize()).isEqualTo(5);
                    assertThat(pageable.getSort().getOrderFor("reward")).isNotNull();
                    return new PageImpl<>(List.of(task), pageable, 6);
                });

        PageResult<TaskDTO> result = taskService.listTasks(3, "published", "快递", "reward", null,2, 5);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getId()).isEqualTo(10L);
        assertThat(result.getPagination().getPage()).isEqualTo(2);
        assertThat(result.getPagination().getPageSize()).isEqualTo(5);
        assertThat(result.getPagination().getTotal()).isEqualTo(6);
        assertThat(result.getPagination().getTotalPages()).isEqualTo(2);
    }

    private static TaskCreateRequest createRequest() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setTitle("帮取快递到宿舍");
        request.setDescription("请帮忙从校门口驿站取一个快递");
        request.setCategoryId(3);
        request.setTaskType("RUNNING");
        request.setExpressReward(new BigDecimal("5.00"));
        request.setPickupLocation("校门口驿站");
        request.setDeliveryLocation("3号宿舍楼下");
        request.setDeadline(LocalDateTime.now().plusDays(1));
        return request;
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setName("同学" + id);
        user.setCreditScore(100);
        return user;
    }

    private static Category category(Integer id, Boolean hasReview) {
        Category category = new Category();
        category.setId(id);
        category.setName("快递代取");
        category.setHasReview(hasReview);
        return category;
    }

    private static Task task(Long id, User requester, TaskStatus status, Category category) {
        ExpressTask task = new ExpressTask();
        task.setId(id);
        task.setRequester(requester);
        task.setCategory(category);
        task.setTitle("帮取快递到宿舍");
        task.setDescription("请帮忙从校门口驿站取一个快递");
        task.setReward(new BigDecimal("5.00"));
        task.setLocation("3号宿舍楼下");
        task.setDeadline(LocalDateTime.now().plusDays(1));
        task.setPublishedAt(LocalDateTime.now());
        task.setStatus(status);
        task.setPickupLocation("校门口驿站");
        task.setDeliveryLocation("3号宿舍楼下");
        task.setExpressReward(new BigDecimal("5.00"));
        return task;
    }
}
