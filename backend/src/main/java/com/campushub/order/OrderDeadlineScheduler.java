package com.campushub.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

/** 调度器不持有批量事务，每单由独立代理事务处理；一笔异常不阻断整批。 */
@Component @RequiredArgsConstructor @Slf4j
public class OrderDeadlineScheduler {
    private final OrderRepository orders;
    private final OrderLifecycleService lifecycle;

    @Scheduled(fixedDelay = 60000)
    public void scan() {
        for (Order order : orders.findByStatusIn(List.of(Order.OrderStatus.awaiting_acceptance, Order.OrderStatus.completed))) {
            try { lifecycle.processDeadline(order.getId()); }
            catch (Exception ex) { log.error("订单期限处理失败，orderId={}", order.getId(), ex); }
        }
    }
}
