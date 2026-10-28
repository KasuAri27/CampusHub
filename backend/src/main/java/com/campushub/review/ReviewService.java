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

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final NotificationService notificationService;

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;

    @Transactional
    public void submitReview(Long orderId, Long currentUserId, Integer score, String comment) {
        if (score == null || score < 1 || score > 5) {
            throw new BusinessException(400, "评分必须在 1 到 5 之间");
        }

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.getRequester().getId().equals(currentUserId)) {
            throw new BusinessException(403, "只有需求方可以评价订单");
        }
        if (order.getStatus() != OrderStatus.completed) {
            throw new BusinessException(400, "只有已完成的订单可以评价");
        }
        if (Boolean.FALSE.equals(order.getTask().getCategory().getHasReview())) {
            throw new BusinessException(400, "该分类不支持评价");
        }
        if (reviewRepository.existsByOrder_Id(orderId)) {
            throw new BusinessException(409, "该订单已评价，请勿重复提交");
        }
        if (!"默认好评".equals(comment) && order.getCompletedAt() != null) {
            if (order.getCompletedAt().plusDays(7).isBefore(LocalDateTime.now())) {
                throw new BusinessException(400, "评价期限已过，系统已默认五星好评");
            }
        }

        Review review = new Review();
        review.setOrder(order);
        review.setScore(score);
        review.setComment(comment);
        reviewRepository.save(review);
        // 通知服务方收到评价
        if ("默认好评".equals(comment)) {
            // 给服务方发送系统通知
            notificationService.createNotification(
                    order.getProvider().getId(),
                    "系统自动评价通知",
                    "您完成的订单《" + order.getTask().getTitle() + "》已超过评价期限，系统已自动为您默认五星好评。",
                    "NEW_REVIEW"
            );
            // 给需求方发送系统通知
            notificationService.createNotification(
                    order.getRequester().getId(),
                    "系统自动评价通知",
                    "您的订单《" + order.getTask().getTitle() + "》因超时未评价，系统已自动默认五星好评。",
                    "SYSTEM"
            );
        } else {
            // 原有的普通（手动）评价通知给服务方
            notificationService.createNotification(
                    order.getProvider().getId(),
                    "收到新评价",
                    "用户 " + order.getRequester().getName() + " 对你的服务给出了 " + score + " 星评价。",
                    "NEW_REVIEW"
            );
        }

        applyCreditScore(order.getProvider(), score);
    }

    @Transactional(readOnly = true)
    public ReviewDTO getReviewByOrderId(Long orderId) {
        return reviewRepository.findByOrder_Id(orderId)
                .map(review -> new ReviewDTO(
                        review.getScore(),
                        review.getComment(),
                        review.getCreatedAt()
                ))
                .orElse(null);
    }

    private void applyCreditScore(User reviewee, Integer score) {
        int delta = 0;
        if (score == 5) {
            delta = 1;
        } else if (score <= 2) {
            delta = -1;
        }
        if (delta != 0) {
            int currentScore = reviewee.getCreditScore() == null ? 0 : reviewee.getCreditScore();
            int newScore = Math.min(100, Math.max(0, currentScore + delta));
            reviewee.setCreditScore(newScore);
            if (newScore < 70) {
                notificationService.createNotification(
                        reviewee.getId(),
                        "信用分不足提醒",
                        "由于收到低星评价，您的信用分已降至 " + newScore + "（低于70），发布权限已被限制。",
                        "CREDIT_WARNING"
                );
            }
        }
    }
}
