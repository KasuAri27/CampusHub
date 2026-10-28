package com.campushub.review;

import com.campushub.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/submit")
    public ApiResponse<Void> submitReview(@RequestParam Long orderId,
                                          @RequestParam Integer score,
                                          @RequestParam(required = false) String comment,
                                          @RequestHeader("X-User-Id") Long currentUserId) {
        reviewService.submitReview(orderId, currentUserId, score, comment);
        return ApiResponse.success();
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<ReviewDTO> getReviewByOrderId(@PathVariable Long orderId) {
        return ApiResponse.success(reviewService.getReviewByOrderId(orderId));
    }
}
