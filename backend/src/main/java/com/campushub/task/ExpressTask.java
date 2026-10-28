package com.campushub.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 跑腿服务 — 继承自 Task，映射到 {@code t_task_running} 表.
 */
@Entity
@Table(name = "t_task_running")
@DiscriminatorValue("RUNNING")
@PrimaryKeyJoinColumn(name = "task_id")
@Getter
@Setter
@NoArgsConstructor
public class ExpressTask extends Task {

    /** 出发/取货地点 */
    @Column(name = "pickup_location", nullable = false, length = 100)
    private String pickupLocation;

    /** 送达/目的地 */
    @Column(name = "delivery_location", nullable = false, length = 100)
    private String deliveryLocation;

    /** 取件码/备注 */
    @Column(name = "pickup_code", length = 50)
    private String pickupCode;

    /** 跑腿费 */
    @Column(name = "sub_reward", nullable = false, precision = 10, scale = 2)
    private BigDecimal expressReward;
}
