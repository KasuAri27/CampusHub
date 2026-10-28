package com.campushub.task;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 二手交易任务。
 */
@Entity
@DiscriminatorValue("FLEA")
@PrimaryKeyJoinColumn(name = "task_id")
@Getter
@Setter
@NoArgsConstructor
public class SecondHandTask extends Task {

    @Column(name = "trade_mode", length = 10)
    private String tradeMode = "SELL";

    @Column(name = "item_price", precision = 10, scale = 2)
    private BigDecimal itemPrice;

    @Column(name = "requester_payment_code")
    private String requesterPaymentCode;
}
