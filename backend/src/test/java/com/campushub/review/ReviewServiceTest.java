package com.campushub.review;

import com.campushub.category.Category;
import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.order.Order;
import com.campushub.order.Order.OrderStatus;
import com.campushub.order.OrderRepository;
import com.campushub.task.ExpressTask;
import com.campushub.task.Task;
import com.campushub.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private NotificationService notificationService;
    @Mock private com.campushub.user.CreditService creditService;

    @InjectMocks
    private ReviewService reviewService;

    @org.junit.jupiter.api.BeforeEach
    void configureClock() {
        reviewService = new ReviewService(notificationService, orderRepository, reviewRepository,
                creditService, com.campushub.support.BusinessFixtures.CLOCK);
    }

    @Test
    void submitReviewSavesReviewAndAddsCreditForFiveStarScore() {
        User requester = user(1L, 100);
        User provider = user(2L, 100);
        Order order = completedOrder(requester, provider, true);
        when(orderRepository.findLockedById(20L)).thenReturn(Optional.of(order));
        when(reviewRepository.existsByOrder_Id(20L)).thenReturn(false);

        reviewService.submitReview(20L, 1L, 5, "非常及时");

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        Review saved = captor.getValue();
        assertThat(saved.getOrder()).isSameAs(order);
        assertThat(saved.getScore()).isEqualTo(5);
        assertThat(saved.getComment()).isEqualTo("非常及时");
        verify(creditService).adjust(2L, 1, "review:20", "订单评分：5");
    }

    @Test
    void submitReviewDeductsCreditForLowScore() {
        User requester = user(1L, 100);
        User provider = user(2L, 100);
        Order order = completedOrder(requester, provider, true);
        when(orderRepository.findLockedById(20L)).thenReturn(Optional.of(order));
        when(reviewRepository.existsByOrder_Id(20L)).thenReturn(false);

        reviewService.submitReview(20L, 1L, 2, "迟到较久");

        verify(creditService).adjust(2L, -1, "review:20", "订单评分：2");
    }

    @Test
    void submitReviewRejectsScoreOutsideOneToFive() {
        assertThatThrownBy(() -> reviewService.submitReview(20L, 1L, 0, "无效评分"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("评分必须在 1 到 5 之间");

        verify(orderRepository, never()).findByIdWithDetails(any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReviewRejectsNonRequester() {
        User requester = user(1L, 100);
        User provider = user(2L, 100);
        Order order = completedOrder(requester, provider, true);
        when(orderRepository.findLockedById(20L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.submitReview(20L, 2L, 5, "越权评价"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有需求方可以评价订单");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReviewRejectsUnsupportedCategory() {
        User requester = user(1L, 100);
        User provider = user(2L, 100);
        Order order = completedOrder(requester, provider, false);
        when(orderRepository.findLockedById(20L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.submitReview(20L, 1L, 5, "无法评价"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该分类不支持评价");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitReviewRejectsDuplicateReview() {
        User requester = user(1L, 100);
        User provider = user(2L, 100);
        Order order = completedOrder(requester, provider, true);
        when(orderRepository.findLockedById(20L)).thenReturn(Optional.of(order));
        when(reviewRepository.existsByOrder_Id(20L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.submitReview(20L, 1L, 5, "重复评价"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该订单已评价，请勿重复提交");

        verify(reviewRepository, never()).save(any());
        assertThat(provider.getCreditScore()).isEqualTo(100);
    }

    private static User user(Long id, Integer creditScore) {
        User user = new User();
        user.setId(id);
        user.setCreditScore(creditScore);
        return user;
    }

    private static Order completedOrder(User requester, User provider, boolean hasReview) {
        Category category = new Category();
        category.setId(1);
        category.setName("快递代取");
        category.setHasReview(hasReview);

        ExpressTask task = new ExpressTask();
        task.setId(10L);
        task.setRequester(requester);
        task.setCategory(category);

        Order order = new Order();
        order.setId(20L);
        order.setRequester(requester);
        order.setProvider(provider);
        order.setTask(task);
        order.setStatus(OrderStatus.completed);
        order.setPayerId(requester.getId());
        order.setPayeeId(provider.getId());
        order.setReviewEligibleAt(com.campushub.support.BusinessFixtures.NOW.minusDays(1));
        return order;
    }
}
