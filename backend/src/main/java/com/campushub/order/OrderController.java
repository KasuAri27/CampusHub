package com.campushub.order;

import com.campushub.common.ApiResponse;
import com.campushub.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
/** 订单履约控制器，负责接单成单、提交交付、确认验收、取消履约及履约历史查询。 */
public class OrderController {

    private final OrderService orderService;
    private final OrderLifecycleService lifecycle;

    @GetMapping("/task/{taskId}")
    public ApiResponse<OrderDTO> getByTaskId(@PathVariable Long taskId) {
        return ApiResponse.success(orderService.getByTaskId(taskId, UserContext.getCurrentUserId()));
    }

    /** 用户接单：严格校验发布状态、截止期、信誉分门槛（>=70）及防自接，生成履约订单。 */
    @PostMapping("/accept")
    public ApiResponse<OrderDTO> acceptTask(@RequestParam Long taskId) {
        Long currentUserId = UserContext.getCurrentUserId();
        return ApiResponse.success(orderService.acceptTask(taskId, currentUserId));
    }

    /** 需求方主动确认验收完成，转入待付款/待评价状态。 */
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

    /** 取消订单：仅进行中且无争议订单允许取消；接单人取消且未过期时需求将自动重新开放。 */
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
