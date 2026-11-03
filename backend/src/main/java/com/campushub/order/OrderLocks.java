package com.campushub.order;

import com.campushub.common.BusinessException;
import com.campushub.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** 履约写操作固定按“任务→订单”加锁，避免接单、组队取消与自动验收互相反向等待。 */
@Component @RequiredArgsConstructor
public class OrderLocks {
    private final OrderRepository orders;
    private final TaskRepository tasks;
    @Transactional(propagation = Propagation.MANDATORY)
    public Order lock(Long orderId) {
        Long taskId = orders.findTaskId(orderId).orElseThrow(() -> new BusinessException(404, "订单不存在"));
        tasks.findLockedById(taskId).orElseThrow(() -> new BusinessException(404, "需求不存在"));
        return orders.findLockedById(orderId).orElseThrow(() -> new BusinessException(404, "订单不存在"));
    }
}
