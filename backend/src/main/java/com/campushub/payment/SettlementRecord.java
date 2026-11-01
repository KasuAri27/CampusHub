package com.campushub.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 人工结算的审计凭证，不冒充支付渠道自动转账结果；同一订单只能登记一次。 */
@Entity @Table(name = "settlement_records") @Getter @Setter
public class SettlementRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long orderId;
    private Long payeeId;
    private Long administratorId;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, unique = true, length = 100)
    private String transferReference;
    @Column(nullable = false, length = 100)
    private String evidenceFilename;
    private LocalDateTime recordedAt;
}
