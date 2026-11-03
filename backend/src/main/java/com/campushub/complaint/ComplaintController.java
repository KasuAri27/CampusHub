package com.campushub.complaint;

import com.campushub.common.ApiResponse;
import com.campushub.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/complaints")
@RequiredArgsConstructor
/**
 * 订单投诉与争议裁决控制器。
 * 支持订单参与双方提交履约纠纷投诉，仅限订单当事人与平台管理员查阅详情。
 */
public class ComplaintController {

    private final ComplaintService complaintService;

    @PostMapping
    public ApiResponse<Void> submit(@RequestBody ComplaintRequest req) {
        complaintService.submitComplaint(UserContext.getCurrentUserId(), req);
        return ApiResponse.success("投诉已提交", null);
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<List<Complaint>> getByOrder(@PathVariable Long orderId) {
        return ApiResponse.success(complaintService.getByOrderId(orderId, UserContext.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Complaint> getById(@PathVariable Long id) {
        return ApiResponse.success(complaintService.getById(id, UserContext.getCurrentUserId()));
    }
}
