package com.campushub.order;

import com.campushub.common.ApiResponse;
import com.campushub.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderLifecycleService lifecycle;

    @GetMapping("/task/{taskId}")
    public ApiResponse<OrderDTO> getByTaskId(@PathVariable Long taskId) {
        return ApiResponse.success(orderService.getByTaskId(taskId, UserContext.getCurrentUserId()));
    }

    @PostMapping("/accept")
    public ApiResponse<OrderDTO> acceptTask(@RequestParam Long taskId) {
        Long currentUserId = UserContext.getCurrentUserId();
        return ApiResponse.success(orderService.acceptTask(taskId, currentUserId));
    }

    @PostMapping("/{orderId}/complete")
    public ApiResponse<Void> completeOrder(@PathVariable Long orderId) {
        Long currentUserId = UserContext.getCurrentUserId();
        orderService.completeOrder(orderId, currentUserId);
        return ApiResponse.success();
    }

    /** 服务方提交完成后等待24小时验收，不从任务截止时间直接推定服务已经完成。 */
    @PostMapping("/{orderId}/submit-completion")
    public ApiResponse<Void> submitCompletion(@PathVariable Long orderId) {
        lifecycle.submitCompletion(orderId, UserContext.getCurrentUserId());
        return ApiResponse.success();
    }

    @GetMapping("/mine")
    public ApiResponse<List<OrderDTO>> mine() {
        return ApiResponse.success(orderService.getMyOrders(UserContext.getCurrentUserId()));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDTO> detail(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.getDetail(orderId, UserContext.getCurrentUserId()));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Void> cancelOrder(@PathVariable Long orderId) {
        Long currentUserId = UserContext.getCurrentUserId();
        orderService.cancelOrder(orderId, currentUserId);
        return ApiResponse.success();
    }

    @GetMapping("/my-accepted")
    public ApiResponse<List<OrderDTO>> getMyAccepted(@RequestParam(required = false) String status) {
        Long currentUserId = UserContext.getCurrentUserId();
        return ApiResponse.success(orderService.getAcceptHistory(currentUserId, status));
    }
}
