package com.campushub.support;

import com.campushub.category.Category;
import com.campushub.order.Order;
import com.campushub.task.ExpressTask;
import com.campushub.task.Task;
import com.campushub.user.User;
import java.math.BigDecimal;
import java.time.*;

/** 完整有效基线；负向测试只破坏目标字段，避免因为无关缺字段碰巧通过。 */
public final class BusinessFixtures {
    private BusinessFixtures() { }
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    public static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    public static User user(long id, User.Role role) {
        User user = new User();
        user.setId(id); user.setRole(role); user.setName("测试用户" + id); user.setStudentId("test" + id);
        user.setCreditScore(100); user.setStatus(1);
        return user;
    }
    public static ExpressTask task(User requester) {
        ExpressTask task = new ExpressTask();
        task.setId(10L); task.setRequester(requester); task.setTitle("帮忙代取测试快递"); task.setDescription("从测试驿站取快递并送到测试宿舍楼");
        task.setStatus(Task.TaskStatus.published); task.setDeadline(NOW.plusDays(1)); task.setReward(new BigDecimal("6.50"));
        task.setPickupLocation("测试驿站"); task.setDeliveryLocation("测试宿舍"); task.setExpressReward(task.getReward());
        Category category = new Category(); category.setId(1); category.setName("测试跑腿"); category.setHasReview(true); task.setCategory(category);
        return task;
    }
    public static Order order() {
        User requester = user(1, User.Role.requester), provider = user(2, User.Role.provider);
        Order order = Order.builder().id(20L).task(task(requester)).requester(requester).provider(provider)
                .status(Order.OrderStatus.in_progress).amountDue(new BigDecimal("6.50")).payerId(1L).payeeId(2L)
                .paymentStatus(Order.PaymentStatus.unpaid).settlementStatus(Order.SettlementStatus.pending).build();
        return order;
    }
}
