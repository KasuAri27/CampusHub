package com.campushub.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.campushub.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 支付宝当面付预创建及查单；回调必须通过官方 RSA2 验签，并再次核对商户和金额。 */
@Component @RequiredArgsConstructor
public class AlipayPaymentGateway implements PaymentGateway {
    private final PaymentProperties properties;
    private final Clock clock;
    private volatile AlipayClient client;
    private String publicKey;
    @Override public PaymentAttempt.Channel channel() { return PaymentAttempt.Channel.ALIPAY; }
    @Override public boolean enabled() { return properties.getAlipay().isEnabled(); }

    private synchronized void initialize() {
        if (!enabled()) throw new BusinessException(503, "支付宝支付尚未启用");
        if (client != null) return;
        var p = properties.getAlipay();
        try {
            String privateKey = pem(p.getPrivateKeyPath());
            publicKey = pem(p.getPublicKeyPath());
            // 沙箱必须显式配置；不会因为生产失败而自动切换到模拟成功或测试网关。
            String gateway = p.isSandbox() ? "https://openapi-sandbox.dl.alipaydev.com/gateway.do" : "https://openapi.alipay.com/gateway.do";
            client = new DefaultAlipayClient(gateway, p.getAppId(), privateKey, "json", "UTF-8", publicKey, "RSA2");
        } catch (Exception ex) { throw new BusinessException(503, "支付宝商户配置不完整，请联系管理员"); }
    }
    private String pem(String file) throws java.io.IOException {
        return Files.readString(Path.of(file)).replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
    }

    @Override public String prepay(String outTradeNo, BigDecimal amount, String subject) {
        initialize();
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(outTradeNo);
        model.setTotalAmount(amount.toPlainString());
        model.setSubject(subject);
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(properties.getAlipay().getNotifyUrl());
        request.setBizModel(model);
        try {
            var response = client.execute(request);
            if (!response.isSuccess()) throw new BusinessException(502, "支付宝下单未成功，请稍后查询或重试原支付单");
            return response.getQrCode();
        } catch (AlipayApiException ex) { throw new BusinessException(502, "支付宝暂不可用，请稍后查询原支付单"); }
    }

    @Override public VerifiedPayment query(String outTradeNo) {
        initialize();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            var response = client.execute(request);
            if (!response.isSuccess()) return new VerifiedPayment(channel(), outTradeNo, null, null, "CNY", null, null, false, null);
            boolean paid = "TRADE_SUCCESS".equals(response.getTradeStatus()) || "TRADE_FINISHED".equals(response.getTradeStatus());
            LocalDateTime paidAt = paid && response.getSendPayDate() != null
                    ? LocalDateTime.ofInstant(response.getSendPayDate().toInstant(), clock.getZone()) : null;
            return new VerifiedPayment(channel(), response.getOutTradeNo(), response.getTradeNo(),
                    response.getTotalAmount() == null ? null : new BigDecimal(response.getTotalAmount()), "CNY",
                    properties.getAlipay().getAppId(), properties.getAlipay().getSellerId(), paid, paidAt, "TRADE_CLOSED".equals(response.getTradeStatus()));
        } catch (AlipayApiException ex) { throw new BusinessException(502, "支付宝查单暂不可用，请稍后重试"); }
    }

    @Override public VerifiedPayment verifyNotification(String rawBody, Map<String, String> parameters) {
        initialize();
        try {
            if (!AlipaySignature.rsaCheckV1(parameters, publicKey, "UTF-8", "RSA2")) throw new BusinessException(400, "支付通知签名无效");
            boolean paid = "TRADE_SUCCESS".equals(parameters.get("trade_status")) || "TRADE_FINISHED".equals(parameters.get("trade_status"));
            LocalDateTime paidAt = paid ? LocalDateTime.parse(parameters.get("gmt_payment"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null;
            return new VerifiedPayment(channel(), parameters.get("out_trade_no"), parameters.get("trade_no"),
                    new BigDecimal(parameters.get("total_amount")), "CNY", parameters.get("app_id"), parameters.get("seller_id"), paid, paidAt,
                    "TRADE_CLOSED".equals(parameters.get("trade_status")));
        } catch (AlipayApiException | IllegalArgumentException ex) { throw new BusinessException(400, "支付通知验签或格式校验失败"); }
    }
}
