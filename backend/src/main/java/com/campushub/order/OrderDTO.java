package com.campushub.order;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
/** 订单核心传输对象，综合关联任务信息、履约双方身份、付款/结算全链路状态及争议信息。 */
public class OrderDTO {

    private Long orderId;
    private String orderStatus;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private Long taskId;
    private String taskStatus;
    private String title;
    private String description;
    private BigDecimal reward;
    private Long requesterId;
    private Long providerId;
    private BigDecimal amountDue;
    private Long payerId;
    private Long payeeId;
    private String paymentStatus;
    private String settlementStatus;
    private LocalDateTime completionSubmittedAt;
    private LocalDateTime acceptanceDueAt;
    private LocalDateTime paymentDueAt;
    private LocalDateTime escalationDueAt;
    private LocalDateTime escalatedAt;
    private LocalDateTime reviewEligibleAt;
    private LocalDateTime paidAt;
    private LocalDateTime settledAt;
    private boolean disputed;
    private String disputeReason;
    private boolean hasReview;
    private String taskType;
}
