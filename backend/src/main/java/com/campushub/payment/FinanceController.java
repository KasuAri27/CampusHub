package com.campushub.payment;

import com.campushub.common.ApiResponse;
import com.campushub.common.BusinessException;
import com.campushub.order.OrderDTO;
import com.campushub.order.OrderLifecycleService;
import com.campushub.order.OrderRepository;
import com.campushub.order.OrderService;
import com.campushub.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.List;

/** 财务操作均由数据库中的管理员身份鉴权；不接收客户端自报管理员ID。 */
@RestController @RequestMapping("/v1/admin/finance") @RequiredArgsConstructor
public class FinanceController {
    private final OrderLifecycleService lifecycle;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final SettlementService settlements;
    private final SettlementRecordRepository records;
    private final SettlementEvidenceStorage storage;
    private final PaymentAttemptRepository attempts;
    public record Resolution(BigDecimal amount, int paymentHours, String reason) { }
    public record Penalty(String operationId, int deduction, boolean ban, String reason) { }

    /** 管理员能核对全部历史支付单，关闭单和迟到成功不能在重开支付单时被覆盖。 */
    @GetMapping("/orders/{id}/payments")
    public ApiResponse<List<PaymentAttempt>> payments(@PathVariable Long id) {
        lifecycle.requireAdmin(UserContext.getCurrentUserId());
        return ApiResponse.success(attempts.findAllByOrderIdOrderByIdDesc(id));
    }

    @GetMapping("/orders") @Transactional(readOnly = true)
    public ApiResponse<List<OrderDTO>> list(@RequestParam(defaultValue = "1") int page) {
        lifecycle.requireAdmin(UserContext.getCurrentUserId());
        if (page < 1) throw new BusinessException(400, "页码必须大于0");
        return ApiResponse.success(orders.findAll(org.springframework.data.domain.PageRequest.of(page - 1, 50,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"))).stream().map(orderService::toDTO).toList());
    }
    @PostMapping("/orders/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long id, @RequestBody Resolution request) {
        lifecycle.resolveDispute(id, UserContext.getCurrentUserId(), request.amount(), request.paymentHours(), request.reason());
        return ApiResponse.success();
    }
    @PostMapping("/orders/{id}/penalty")
    public ApiResponse<Void> penalty(@PathVariable Long id, @RequestBody Penalty request) {
        lifecycle.penalizeOverdue(id, UserContext.getCurrentUserId(), request.operationId(), request.deduction(), request.ban(), request.reason());
        return ApiResponse.success();
    }
    @PostMapping(value = "/orders/{id}/settlement", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> settle(@PathVariable Long id, @RequestParam BigDecimal amount,
                                    @RequestParam String reference, @RequestParam MultipartFile evidence) {
        return ApiResponse.success(settlements.record(id, UserContext.getCurrentUserId(), amount, reference, evidence).getId());
    }
    @GetMapping("/orders/{id}/evidence")
    public ResponseEntity<Resource> evidence(@PathVariable Long id) {
        lifecycle.requireAdmin(UserContext.getCurrentUserId());
        var record = records.findByOrderId(id).orElseThrow(() -> new BusinessException(404, "尚无结算凭证"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.getEvidenceFilename() + "\"")
                .header("X-Content-Type-Options", "nosniff").body(storage.read(record.getEvidenceFilename()));
    }
}
