package com.campushub.review;

import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.order.Order;
import com.campushub.order.Order.OrderStatus;
import com.campushub.order.OrderRepository;
import com.campushub.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
import com.campushub.user.CreditService;

@Service
@RequiredArgsConstructor
/**
 * 履约评价核心领域服务。
 * 负责7天评价时效校验、分类评价准入、自动四星兜底及信用分联动的单向结算。
 */
public class ReviewService {

    private final NotificationService notificationService;

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final CreditService creditService;
    private final Clock clock;

    @Transactional
    public void submitReview(Long orderId, Long currentUserId, Integer score, String comment) {
        if (score == null || score < 1 || score > 5) {
            throw new BusinessException(400, "评分必须在 1 到 5 之间");
        }

        Order order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!java.util.Objects.equals(order.getPayerId(), currentUserId)) {
            throw new BusinessException(403, "只有需求方可以评价订单");
        }
        if (order.getStatus() != OrderStatus.completed) {
            throw new BusinessException(400, "只有已完成的订单可以评价");
        }
        if (order.isDisputed() || order.getReviewEligibleAt() == null) {
            throw new BusinessException(409, "须先验收、完成有偿付款并处理争议，才能评价");
        }
        if (comment != null && comment.length() > 200) throw new BusinessException(400, "评价内容不能超过200字");
        if (Boolean.FALSE.equals(order.getTask().getCategory().getHasReview())) {
            throw new BusinessException(400, "该分类不支持评价");
        }
        if (reviewRepository.existsByOrder_Id(orderId)) {
            throw new BusinessException(409, "该订单已评价，请勿重复提交");
        }
        if (!LocalDateTime.now(clock).isBefore(order.getReviewEligibleAt().plusDays(7))) {
            throw new BusinessException(400, "评价期限已过，系统将默认四星");
        }
        saveReview(order, score, comment, false);
    }

    /** 自动四星走独立入口，防止用户提交特殊评论绕过七天限制或伪造系统评价。 */
    @Transactional
    public void submitDefaultReview(Long orderId) {
        Order order = orderRepository.findLockedById(orderId).orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (order.isDisputed() || order.getStatus() != OrderStatus.completed || order.getReviewEligibleAt() == null
                || LocalDateTime.now(clock).isBefore(order.getReviewEligibleAt().plusDays(7))
                || !Boolean.TRUE.equals(order.getTask().getCategory().getHasReview()) || reviewRepository.existsByOrder_Id(orderId)) return;
        saveReview(order, 4, "系统默认四星", true);
    }

    private void saveReview(Order order, int score, String comment, boolean automatic) {
        Review review = new Review();
        review.setOrder(order);
        review.setScore(score);
        review.setComment(comment);
        review.setAutomatic(automatic);
        reviewRepository.save(review);
        // 通知服务方收到评价
        if (automatic) {
            // 给服务方发送系统通知
            notificationService.createNotification(
                    order.getPayeeId(),
                    "系统自动评价通知",
                    "订单《" + order.getTask().getTitle() + "》超过评价期限，系统已默认四星，本次不增加信用分。",
                    "NEW_REVIEW"
            );
            // 给需求方发送系统通知
            notificationService.createNotification(
                    order.getPayerId(),
                    "系统自动评价通知",
                    "您的订单《" + order.getTask().getTitle() + "》因超时未评价，系统已默认四星。",
                    "SYSTEM"
            );
        } else {
            // 原有的普通（手动）评价通知给服务方
            notificationService.createNotification(
                    order.getPayeeId(),
                    "收到新评价",
                    "用户 " + order.getRequester().getName() + " 对你的服务给出了 " + score + " 星评价。",
                    "NEW_REVIEW"
            );
        }

        // 单向评价只影响服务提供方；默认四星与手动四星均不加减分。
        int delta = score == 5 ? 1 : score <= 2 ? -1 : 0;
        creditService.adjust(order.getPayeeId(), delta, "review:" + order.getId(), automatic ? "系统默认四星" : "订单评分：" + score);
    }

    @Transactional(readOnly = true)
    public ReviewDTO getReviewByOrderId(Long orderId) {
        return reviewRepository.findByOrder_Id(orderId)
                .map(review -> new ReviewDTO(
                        review.getScore(),
                        review.getComment(),
                        review.getCreatedAt(), review.isAutomatic()
                ))
                .orElse(null);
    }

}
