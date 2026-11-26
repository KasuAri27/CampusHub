package com.campushub.task;

import com.campushub.order.Order;
import com.campushub.order.OrderRepository;
import com.campushub.task.Task.TaskStatus;
import com.campushub.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 聊天视图的字段映射与批量行为；hasOpenObligations 的 SQL 语义由集成测试在真实库里验证。 */
@ExtendWith(MockitoExtension.class)
class TaskReadServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock OrderRepository orderRepository;
    @InjectMocks TaskReadService service;

    @Test
    void mapsTeamTaskFieldsAndObligationFlag() {
        TeamingTask team = new TeamingTask();
        team.setId(10L);
        team.setTitle("组队参赛");
        team.setStatus(TaskStatus.completed);
        team.setRequester(requester(1L));
        team.setMaxMembers(5);
        team.setDeadline(LocalDateTime.of(2026, 11, 20, 18, 0));
        team.setUpdatedAt(LocalDateTime.of(2026, 11, 20, 9, 0));
        when(orderRepository.findTaskIdsWithOpenObligations(any(), any(), any(), any())).thenReturn(List.of(10L));
        when(taskRepository.findAllById(any())).thenReturn(List.of(team));

        TaskChatView view = service.findChatView(10L).orElseThrow();

        assertThat(view).isEqualTo(new TaskChatView(10L, true, "组队参赛", "completed", 1L, 5,
                LocalDateTime.of(2026, 11, 20, 18, 0), LocalDateTime.of(2026, 11, 20, 9, 0), true));
    }

    @Test
    void nonTeamTaskHasNullMaxMembersAndNoObligationsByDefault() {
        ExpressTask express = new ExpressTask();
        express.setId(11L);
        express.setTitle("代取快递");
        express.setStatus(TaskStatus.published);
        express.setRequester(requester(2L));
        when(orderRepository.findTaskIdsWithOpenObligations(any(), any(), any(), any())).thenReturn(List.of());
        when(taskRepository.findAllById(any())).thenReturn(List.of(express));

        TaskChatView view = service.findChatView(11L).orElseThrow();

        assertThat(view.team()).isFalse();
        assertThat(view.maxMembers()).isNull();
        assertThat(view.hasOpenObligations()).isFalse();
    }

    @Test
    void batchQueriesOnceAndOmitsMissingTasks() {
        ExpressTask a = new ExpressTask();
        a.setId(1L); a.setTitle("a"); a.setStatus(TaskStatus.published); a.setRequester(requester(1L));
        ExpressTask b = new ExpressTask();
        b.setId(2L); b.setTitle("b"); b.setStatus(TaskStatus.cancelled); b.setRequester(requester(1L));
        when(orderRepository.findTaskIdsWithOpenObligations(any(), any(), any(), any())).thenReturn(List.of(2L));
        when(taskRepository.findAllById(any())).thenReturn(List.of(a, b));

        Map<Long, TaskChatView> views = service.findChatViews(List.of(1L, 2L, 99L));

        assertThat(views.keySet()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(views.get(1L).hasOpenObligations()).isFalse();
        assertThat(views.get(2L).hasOpenObligations()).isTrue();
        // 用 Order 枚举的真实取值调用聚合查询，且批量路径只发一次。
        verify(orderRepository).findTaskIdsWithOpenObligations(Set.of(1L, 2L, 99L),
                Order.PaymentStatus.unpaid, Order.PaymentStatus.paid, Order.SettlementStatus.recorded);
    }

    @Test
    void emptyOrNullInputsDoNotTouchRepositories() {
        assertThat(service.findChatView(null)).isEmpty();
        assertThat(service.findChatViews(List.of())).isEmpty();
        assertThat(service.findChatViews(null)).isEmpty();

        verify(taskRepository, never()).findAllById(any());
        verify(orderRepository, never()).findTaskIdsWithOpenObligations(any(), any(), any(), any());
    }

    private static User requester(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
