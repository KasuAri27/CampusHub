package com.campushub.payment;

import com.campushub.common.BusinessException;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

/** 微信 Native 真正生成渠道二维码；签名、响应验签及通知 AES-GCM 解密交给官方 SDK。 */
@Component @RequiredArgsConstructor
public class WechatPaymentGateway implements PaymentGateway {
    private final PaymentProperties properties;
    private final Clock clock;
    private volatile RSAPublicKeyConfig config;
    private volatile NativePayService service;
    @Override public PaymentAttempt.Channel channel() { return PaymentAttempt.Channel.WECHAT; }
    @Override public boolean enabled() { return properties.getWechat().isEnabled(); }

    private synchronized void initialize() {
        if (!enabled()) throw new BusinessException(503, "微信支付尚未启用");
        if (service != null) return;
        var p = properties.getWechat();
        try {
            // 显式绑定微信支付公钥ID，不在启动阶段下载证书，也不信任通知自带的公钥。
            config = new RSAPublicKeyConfig.Builder().merchantId(p.getMerchantId())
                    .privateKeyFromPath(p.getPrivateKeyPath()).merchantSerialNumber(p.getMerchantSerialNumber())
                    .publicKeyFromPath(p.getPublicKeyPath()).publicKeyId(p.getPublicKeyId()).apiV3Key(p.getApiV3Key()).build();
            service = new NativePayService.Builder().config(config).build();
        } catch (RuntimeException ex) { throw new BusinessException(503, "微信商户配置不完整，请联系管理员"); }
    }

    @Override public String prepay(String outTradeNo, BigDecimal amount, String subject) {
        initialize();
        PrepayRequest request = new PrepayRequest();
        request.setAppid(properties.getWechat().getAppId());
        request.setMchid(properties.getWechat().getMerchantId());
        request.setOutTradeNo(outTradeNo);
        request.setDescription(subject.substring(0, Math.min(subject.length(), 60)));
        request.setNotifyUrl(properties.getWechat().getNotifyUrl());
        Amount money = new Amount();
        money.setCurrency("CNY");
        money.setTotal(amount.movePointRight(2).intValueExact());
        request.setAmount(money);
        return service.prepay(request).getCodeUrl();
    }

    @Override public VerifiedPayment query(String outTradeNo) {
        initialize();
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(properties.getWechat().getMerchantId());
        request.setOutTradeNo(outTradeNo);
        return verified(service.queryOrderByOutTradeNo(request));
    }

    @Override public VerifiedPayment verifyNotification(String rawBody, Map<String, String> headers) {
        initialize();
        RequestParam request = new RequestParam.Builder().serialNumber(headers.get("wechatpay-serial"))
                .nonce(headers.get("wechatpay-nonce")).timestamp(headers.get("wechatpay-timestamp"))
                .signature(headers.get("wechatpay-signature")).body(rawBody).build();
        return verified(new NotificationParser(config).parse(request, Transaction.class));
    }

    private VerifiedPayment verified(Transaction transaction) {
        boolean paid = transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS;
        java.time.LocalDateTime paidAt = paid ? OffsetDateTime.parse(transaction.getSuccessTime())
                .atZoneSameInstant(clock.getZone()).toLocalDateTime() : null;
        return new VerifiedPayment(channel(), transaction.getOutTradeNo(), transaction.getTransactionId(),
                transaction.getAmount() == null ? null : BigDecimal.valueOf(transaction.getAmount().getTotal(), 2),
                transaction.getAmount() == null ? null : transaction.getAmount().getCurrency(),
                transaction.getAppid(), transaction.getMchid(), paid, paidAt, transaction.getTradeState() == Transaction.TradeStateEnum.CLOSED);
    }
}
