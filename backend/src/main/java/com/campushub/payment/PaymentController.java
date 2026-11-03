package com.campushub.payment;

import com.campushub.common.ApiResponse;
import com.campushub.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用户只能创建自己的付款单。两个匿名回调入口使用渠道签名认证，不使用浏览器 JWT。 */
@RestController @RequestMapping("/v1/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService service;
    public record CreateRequest(@NotNull PaymentAttempt.Channel channel) { }
    @GetMapping("/channels")
    public ApiResponse<List<PaymentAttempt.Channel>> channels() { return ApiResponse.success(service.availableChannels()); }
    @PostMapping("/orders/{orderId}")
    public ApiResponse<PaymentService.PaymentView> create(@PathVariable Long orderId, @Valid @RequestBody CreateRequest request) {
        return ApiResponse.success(service.create(orderId, UserContext.getCurrentUserId(), request.channel()));
    }
    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentService.PaymentView> query(@PathVariable Long orderId) {
        return ApiResponse.success(service.query(orderId, UserContext.getCurrentUserId()));
    }
    @PostMapping("/notify/wechat")
    public ResponseEntity<Void> wechat(@RequestBody String body, @RequestHeader Map<String, String> headers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        headers.forEach((key, value) -> normalized.put(key.toLowerCase(java.util.Locale.ROOT), value));
        service.notify(PaymentAttempt.Channel.WECHAT, body, normalized);
        return ResponseEntity.noContent().build();
    }
    @PostMapping(value = "/notify/alipay", produces = "text/plain;charset=UTF-8")
    public String alipay(HttpServletRequest request) {
        Map<String, String> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length != 1) throw new com.campushub.common.BusinessException(400, "重复通知参数不合法");
            parameters.put(key, values[0]);
        });
        service.notify(PaymentAttempt.Channel.ALIPAY, "", parameters);
        return "success";
    }
}
