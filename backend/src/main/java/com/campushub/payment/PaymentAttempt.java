package com.campushub.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 每单最多一张有效支付单；旧渠道确认关闭后才可新建，历史记录不覆盖。 */
@Entity
@Table(name = "payment_attempts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"channel", "provider_transaction_id"}),
        @UniqueConstraint(columnNames = {"order_id", "active_slot"})})
@Getter @Setter
public class PaymentAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long orderId;
    /** 1表示仍可支付或已付款；关闭置NULL，利用唯一键限制一张有效支付单。 */
    private Integer activeSlot = 1;
    @Column(nullable = false, unique = true, length = 32)
    private String outTradeNo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Channel channel;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    private String appId;
    private String merchantId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;
    @Column(length = 1500)
    private String codeUrl;
    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime lastQueriedAt;
    private LocalDateTime closedAt;
    public enum Channel { WECHAT, ALIPAY }
    public enum Status { PENDING, PAID, CLOSED }
}
