package com.campushub.payment;

import com.campushub.common.BusinessException;
import com.campushub.notification.NotificationService;
import com.campushub.order.Order;
import com.campushub.order.OrderLifecycleService;
import com.campushub.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/** 本版获授权的人工结算流程：管理员先实际转账，再上传凭证登记，不调用虚假自动打款。 */
@Service @RequiredArgsConstructor
public class SettlementService {
    private final OrderRepository orders;
    private final SettlementRecordRepository records;
    private final SettlementEvidenceStorage storage;
    private final OrderLifecycleService authorization;
    private final NotificationService notifications;
    private final PaymentProperties properties;
    private final Clock clock;

    @Transactional
    public SettlementRecord record(Long orderId, Long adminId, BigDecimal amount, String reference, MultipartFile evidence) {
        authorization.requireAdmin(adminId);
        if (!properties.isManualSettlementEnabled()) throw new BusinessException(503, "人工结算尚未启用");
        Order order = orders.findLockedById(orderId).orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (order.isDisputed() || order.getPaymentStatus() != Order.PaymentStatus.paid) throw new BusinessException(409, "须先确认收款并处理所有争议");
        if (amount == null || amount.compareTo(order.getAmountDue()) != 0 || reference == null || reference.isBlank() || reference.length() > 100)
            throw new BusinessException(400, "结算金额必须与已收款金额一致，并提供实际转账流水号");
        var existing = records.findByOrderId(orderId);
        if (existing.isPresent()) {
            if (!existing.get().getTransferReference().equals(reference)) throw new BusinessException(409, "订单已登记其他结算流水");
            return existing.get();
        }
        String filename = storage.store(evidence);
        // 数据库提交失败时移除新文件，避免把没有审计记录的文件误认为有效凭证。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.deleteAfterRollback(filename);
            }
        });
        SettlementRecord record = new SettlementRecord();
        record.setOrderId(orderId);
        record.setPayeeId(order.getPayeeId());
        record.setAdministratorId(adminId);
        record.setAmount(amount);
        record.setTransferReference(reference);
        record.setEvidenceFilename(filename);
        record.setRecordedAt(LocalDateTime.now(clock));
        records.save(record);
        order.setSettlementStatus(Order.SettlementStatus.recorded);
        order.setSettledAt(record.getRecordedAt());
        notifications.createNotification(order.getPayeeId(), "人工结算已登记", "管理员已登记订单#" + orderId + "的实际转账凭证。如有疑问请申请人工核对。", "SETTLEMENT_RECORDED");
        return record;
    }
}
