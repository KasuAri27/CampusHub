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

    @InjectMocks
    private TaskService taskService;

    @Test
    void createTaskAllowsRequesterAndReturnsDto() {
        User requester = user(1L, Role.requester);
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
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.published);
        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getCategoryId()).isEqualTo(3);
        assertThat(dto.getHasReview()).isTrue();
        assertThat(dto.getPublisher().getUserId()).isEqualTo(1L);
    }

    @Test
    void createTaskRejectsProviderRole() {
        User provider = user(2L, Role.provider);
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        assertThatThrownBy(() -> taskService.createTask(createRequest(), 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅需求方可以发布需求");

        verify(categoryRepository, never()).findById(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void deleteOwnTaskAllowsOnlyOwnerAndPublishedTask() {
        User requester = user(1L, Role.requester);
        Task task = task(10L, requester, TaskStatus.published, category(3, true));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        taskService.deleteOwnTask(1L, 10L);

        verify(taskRepository).delete(task);
    }

    @Test
    void deleteOwnTaskRejectsNonOwner() {
        User requester = user(1L, Role.requester);
        Task task = task(10L, requester, TaskStatus.published, category(3, true));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.deleteOwnTask(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能删除自己发布的需求");

        verify(taskRepository, never()).delete((Task) any());
    }

    @Test
    void listTasksUsesOneBasedPaginationAndRewardSort() {
        User requester = user(1L, Role.requester);
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
