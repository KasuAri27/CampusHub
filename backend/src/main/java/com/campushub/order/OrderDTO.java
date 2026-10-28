package com.campushub.order;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
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
    private String paymentCode;
}