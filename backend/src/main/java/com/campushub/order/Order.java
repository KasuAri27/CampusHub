package com.campushub.order;

import com.campushub.task.Task;
import com.campushub.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity(name = "TaskOrder")
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private OrderStatus status = OrderStatus.in_progress;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 接单时冻结总金额与买卖双方，二手出售时买家是接单人，不按用户角色猜资金方向。 */
    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal amountDue;
    private Long payerId;
    private Long payeeId;

    /** 履约、支付、结算分开：completed 只表示已验收，不代表资金已经到账。 */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.not_required;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SettlementStatus settlementStatus = SettlementStatus.not_required;
    private LocalDateTime completionSubmittedAt;
    private LocalDateTime acceptanceDueAt;
    private LocalDateTime paymentDueAt;
    private LocalDateTime overdueAt;
    private LocalDateTime escalationDueAt;
    private LocalDateTime escalatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime reviewEligibleAt;
    private LocalDateTime settledAt;

    /** 争议打开后扫描器只跳过，不把暂停误当付款；裁决重新给出付款期限。 */
    @Builder.Default
    private boolean disputed = false;
    private LocalDateTime disputedAt;
    @Column(length = 1000)
    private String disputeReason;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum OrderStatus {
        in_progress,
        awaiting_acceptance,
        completed,
        cancelled
    }

    public enum PaymentStatus { not_required, unpaid, paid }
    public enum SettlementStatus { not_required, pending, recorded }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (acceptedAt == null) {
            acceptedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
