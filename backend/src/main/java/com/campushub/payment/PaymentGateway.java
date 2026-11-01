package com.campushub.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** 渠道适配器只处理真实 SDK 协议，业务状态写入统一由事务服务完成。 */
public interface PaymentGateway {
    PaymentAttempt.Channel channel();
    boolean enabled();
    String prepay(String outTradeNo, BigDecimal amount, String subject);
    VerifiedPayment query(String outTradeNo);
    VerifiedPayment verifyNotification(String rawBody, Map<String, String> parameters);

    /** 只能由完成渠道验签的适配器构造；HTTP 层不存在提交此对象的接口。 */
    record VerifiedPayment(PaymentAttempt.Channel channel, String outTradeNo, String transactionId,
                           BigDecimal amount, String currency, String appId, String merchantId,
                           boolean paid, LocalDateTime paidAt, boolean closed) {
        public VerifiedPayment(PaymentAttempt.Channel channel, String outTradeNo, String transactionId,
                               BigDecimal amount, String currency, String appId, String merchantId,
                               boolean paid, LocalDateTime paidAt) {
            this(channel, outTradeNo, transactionId, amount, currency, appId, merchantId, paid, paidAt, false);
        }
    }
}
