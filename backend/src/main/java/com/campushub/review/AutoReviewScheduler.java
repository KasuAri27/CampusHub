package com.campushub.review;

import com.campushub.order.Order;
import com.campushub.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoReviewScheduler {

    private final OrderRepository orderRepository;
    private final ReviewService reviewService;

    /**
     * 定时任务：每分钟执行一次，检查是否有符合自动好评条件的订单
     */
    @Scheduled(fixedRate = 60000)
    // 1. 移除这里的 @Transactional，让循环内的每次调用独立控制事务
    public void processAutoReviews() {
        LocalDateTime thresholdTime = LocalDateTime.now().minusDays(7);

        // 获取所有满足条件的订单
        List<Order> pendingOrders = orderRepository.findOrdersPendingAutoReview(
                Order.OrderStatus.completed,
                thresholdTime
        );

        if (!pendingOrders.isEmpty()) {
            log.info("检测到 {} 个订单满足自动评价条件，正在处理...", pendingOrders.size());
        }

        for (Order order : pendingOrders) {
            try {
                // 2. 将字符串修改为 "默认好评"，与 ReviewService 保持一致
                reviewService.submitReview(
                        order.getId(),
                        order.getRequester().getId(),
                        5,
                        "默认好评"
                );
                log.info("订单 ID: {} 自动五星好评处理成功。", order.getId());
            } catch (Exception e) {
                log.error("订单 ID: {} 自动评价处理失败: {}", order.getId(), e.getMessage());
            }
        }
    }
}