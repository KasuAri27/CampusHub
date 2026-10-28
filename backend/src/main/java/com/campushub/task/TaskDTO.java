package com.campushub.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求响应 DTO。
 */
@Data
@Builder
@AllArgsConstructor
public class TaskDTO {

    private Long id;
    private String title;
    private String description;
    private String categoryName;
    private Integer categoryId;
    private Boolean hasReview;
    private BigDecimal reward;
    private String status;
    private String taskType;
    private String campus;
    private String location;
    private LocalDateTime deadline;
    private int viewCount;
    private LocalDateTime publishedAt;
    private Publisher publisher;

    private String pickupLocation;
    private String deliveryLocation;
    private String pickupCode;
    private BigDecimal expressReward;

    private String subject;
    private BigDecimal pricePerHour;
    private String tutoringLocation;

    private String tradeMode;
    private BigDecimal itemPrice;

    private int maxMembers;
    private Long chatGroupId;

    private String consultingMode;

    @Data
    @Builder
    @AllArgsConstructor
    public static class Publisher {
        private Long userId;
        private String nickname;
        private String avatar;
        private Integer creditScore;
    }
}
