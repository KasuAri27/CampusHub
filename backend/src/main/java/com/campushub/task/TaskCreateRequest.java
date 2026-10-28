package com.campushub.task;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发布需求的请求体，包含基础字段和各子类专属字段。
 */
@Data
public class TaskCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(min = 5, max = 50, message = "标题长度需在 5-50 字符之间")
    private String title;

    @NotBlank(message = "描述不能为空")
    @Size(min = 10, max = 500, message = "描述长度需在 10-500 字符之间")
    private String description;

    @NotNull(message = "分类ID不能为空")
    private Integer categoryId;

    @NotNull(message = "任务类型不能为空")
    @Pattern(regexp = "RUNNING|TUTORING|FLEA|TEAM|CONSULTING", message = "无效的任务类型")
    private String taskType;

    @DecimalMin(value = "0", inclusive = true, message = "报酬不能为负数")
    @DecimalMax(value = "99999999.99", message = "报酬超出合理范围")
    private BigDecimal reward;

    private String campus;

    private String location;

    @NotNull(message = "截止时间不能为空")
    @Future(message = "截止时间必须晚于当前时间")
    private LocalDateTime deadline;

    // RUNNING
    private String pickupLocation;
    private String deliveryLocation;
    private String pickupCode;
    private BigDecimal expressReward;

    // TUTORING
    private String subject;
    private BigDecimal pricePerHour;
    private String tutoringLocation;

    // FLEA
    private String tradeMode;
    private BigDecimal itemPrice;

    // TEAM
    private int maxMembers;

    // CONSULTING
    private String consultingMode;
}
