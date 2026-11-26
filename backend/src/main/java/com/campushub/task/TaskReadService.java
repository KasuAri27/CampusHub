package com.campushub.task;

import com.campushub.order.Order;
import com.campushub.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 需求只读视图服务（模块契约 §3.6）：消息集群读取需求信息的唯一入口。
 * 只读事务，不加锁、不写任何表；订单状态的解释留在交易核心内部，聊天侧只拿到布尔结论。
 */
@Service
@RequiredArgsConstructor
public class TaskReadService {

    private final TaskRepository taskRepository;
    private final OrderRepository orderRepository;

    /** 单个需求的聊天视图；需求不存在返回空。 */
    @Transactional(readOnly = true)
    public Optional<TaskChatView> findChatView(Long taskId) {
        if (taskId == null) return Optional.empty();
        return Optional.ofNullable(findChatViews(List.of(taskId)).get(taskId));
    }

    /**
     * 批量查询，供会话列表与群聊清理任务使用：需求一次查出，订单义务用一条聚合查询判定，
     * 不会因会话数量产生逐条查询。不存在的需求 ID 不出现在结果里。
     */
    @Transactional(readOnly = true)
    public Map<Long, TaskChatView> findChatViews(Collection<Long> taskIds) {
        Map<Long, TaskChatView> views = new HashMap<>();
        if (taskIds == null || taskIds.isEmpty()) return views;
        Set<Long> ids = new HashSet<>(taskIds);
        ids.remove(null);
        if (ids.isEmpty()) return views;

        Set<Long> withObligations = new HashSet<>(orderRepository.findTaskIdsWithOpenObligations(
                ids, Order.PaymentStatus.unpaid, Order.PaymentStatus.paid, Order.SettlementStatus.recorded));
        for (Task loaded : taskRepository.findAllById(ids)) {
            // 同一持久化上下文里该需求可能已被订单关联以懒加载代理载入，instanceof 对代理恒为假，先还原真实子类。
            Task task = (Task) Hibernate.unproxy(loaded);
            boolean team = task instanceof TeamingTask;
            views.put(task.getId(), new TaskChatView(
                    task.getId(),
                    team,
                    task.getTitle(),
                    task.getStatus().name(),
                    task.getRequester().getId(),
                    team ? ((TeamingTask) task).getMaxMembers() : null,
                    task.getDeadline(),
                    task.getUpdatedAt(),
                    withObligations.contains(task.getId())));
        }
        return views;
    }
}
