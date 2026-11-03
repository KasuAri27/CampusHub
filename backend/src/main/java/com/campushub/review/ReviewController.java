package com.campushub.review;

import com.campushub.common.ApiResponse;
import com.campushub.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/reviews")
@RequiredArgsConstructor
/** 订单评价控制器，提供需求方履约评价提交与订单评价记录查询。 */
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 提交服务评价：仅限付款需求方在完成或付款后7天内提交；
     * 5星增加服务方信用分，1-2星扣减信用分，3-4星不影响分数。
     */
    @PostMapping("/submit")
    public ApiResponse<Void> submitReview(@RequestParam Long orderId,
                                          @RequestParam Integer score,
                                          @RequestParam(required = false) String comment) {
        Long currentUserId = UserContext.getCurrentUserId();
        reviewService.submitReview(orderId, currentUserId, score, comment);
        return ApiResponse.success();
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<ReviewDTO> getReviewByOrderId(@PathVariable Long orderId) {
        return ApiResponse.success(reviewService.getReviewByOrderId(orderId));
    }
}
