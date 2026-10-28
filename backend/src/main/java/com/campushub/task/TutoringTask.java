package com.campushub.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 学习辅导 — 继承自 Task，映射到 {@code t_task_tutoring} 表.
 */
@Entity
@Table(name = "t_task_tutoring")
@DiscriminatorValue("TUTORING")
@PrimaryKeyJoinColumn(name = "task_id")
@Getter
@Setter
@NoArgsConstructor
public class TutoringTask extends Task {

    /** 辅导科目 */
    @Column(nullable = false, length = 50)
    private String subject;

    /** 时薪 */
    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    /** 辅导地点 */
    @Column(name = "tutoring_location", nullable = false, length = 100)
    private String tutoringLocation;
}
