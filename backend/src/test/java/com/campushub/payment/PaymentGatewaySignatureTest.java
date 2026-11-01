package com.campushub.payment;

import com.alipay.api.internal.util.AlipaySignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import static com.campushub.support.BusinessFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** 用现场生成的测试RSA密钥和真实官方SDK验签，证明不是“Mock说成功所以测试成功”。 */
class PaymentGatewaySignatureTest {
    @TempDir Path directory;
    final ObjectMapper json = new ObjectMapper();
    KeyPair keyPair() throws Exception { KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
    String file(String name, byte[] key, boolean privateKey) throws Exception {
        String type = privateKey ? "PRIVATE KEY" : "PUBLIC KEY";
        Path path = directory.resolve(name);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(key) + "\n-----END " + type + "-----\n");
        return path.toString();
    }
    @Test void wechatAuthenticatesAndDecryptsRealNotificationAndRejectsTampering() throws Exception {
        KeyPair merchant = keyPair(), platform = keyPair(); PaymentProperties properties = new PaymentProperties();
        var config = properties.getWechat(); config.setEnabled(true); config.setAppId("app"); config.setMerchantId("merchant");
        config.setMerchantSerialNumber("0123456789ABCDEF"); config.setPublicKeyId("PUB_KEY_ID_TEST");
        config.setApiV3Key("0123456789abcdef0123456789abcdef");
        config.setPrivateKeyPath(file("merchant.pem", merchant.getPrivate().getEncoded(), true));
        config.setPublicKeyPath(file("platform.pem", platform.getPublic().getEncoded(), false));
        WechatPaymentGateway gateway = new WechatPaymentGateway(properties, CLOCK);
        String clear = json.writeValueAsString(Map.of("appid", "app", "mchid", "merchant", "out_trade_no", "order-no",
                "transaction_id", "wx-transaction", "trade_state", "SUCCESS", "success_time", "2026-10-31T12:00:00+08:00",
                "amount", Map.of("total", 650, "currency", "CNY")));
        String nonce = "0123456789ab", associated = "transaction";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(config.getApiV3Key().getBytes(StandardCharsets.UTF_8), "AES"), new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
        String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(clear.getBytes(StandardCharsets.UTF_8)));
        String body = json.writeValueAsString(Map.of("id", "event-id", "create_time", "2026-10-31T12:00:00+08:00", "event_type", "TRANSACTION.SUCCESS",
                "resource_type", "encrypt-resource", "resource", Map.of("algorithm", "AEAD_AES_256_GCM", "ciphertext", encrypted, "nonce", nonce, "associated_data", associated, "original_type", "transaction")));
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(platform.getPrivate());
        signer.update((timestamp + "\nheader-nonce\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        Map<String,String> headers = Map.of("wechatpay-serial", config.getPublicKeyId(), "wechatpay-timestamp", timestamp,
                "wechatpay-nonce", "header-nonce", "wechatpay-signature", Base64.getEncoder().encodeToString(signer.sign()));
        var verified = gateway.verifyNotification(body, headers);
        assertThat(verified.paid()).isTrue(); assertThat(verified.amount()).isEqualByComparingTo("6.50");
        assertThat(verified.merchantId()).isEqualTo("merchant");
        assertThatThrownBy(() -> gateway.verifyNotification(body.replace("event-id", "changed-event"), headers)).isInstanceOf(RuntimeException.class);
    }
    @Test void alipayAuthenticatesRealRsa2NotificationAndRejectsAmountTampering() throws Exception {
        KeyPair merchant = keyPair(), platform = keyPair(); PaymentProperties properties = new PaymentProperties();
        var config = properties.getAlipay(); config.setEnabled(true); config.setAppId("app"); config.setSellerId("seller");
        config.setPrivateKeyPath(file("ali-merchant.pem", merchant.getPrivate().getEncoded(), true));
        config.setPublicKeyPath(file("ali-platform.pem", platform.getPublic().getEncoded(), false));
        AlipayPaymentGateway gateway = new AlipayPaymentGateway(properties, CLOCK);
        Map<String,String> values = new TreeMap<>(Map.of("app_id", "app", "seller_id", "seller", "out_trade_no", "order-no",
                "trade_no", "ali-transaction", "total_amount", "6.50", "trade_status", "TRADE_SUCCESS", "gmt_payment", "2026-10-31 12:00:00"));
        String signature = AlipaySignature.rsaSign(AlipaySignature.getSignContent(values), Base64.getEncoder().encodeToString(platform.getPrivate().getEncoded()), "UTF-8", "RSA2");
        values.put("sign_type", "RSA2"); values.put("sign", signature);
        var verified = gateway.verifyNotification("", new TreeMap<>(values));
        assertThat(verified.paid()).isTrue(); assertThat(verified.amount()).isEqualByComparingTo("6.50");
        values.put("total_amount", "0.01");
        assertThatThrownBy(() -> gateway.verifyNotification("", new TreeMap<>(values))).isInstanceOf(com.campushub.common.BusinessException.class);
    }
    @Test void disabledChannelsFailClosedInsteadOfReturningFakeQrCode() {
        PaymentProperties properties = new PaymentProperties();
        assertThatThrownBy(() -> new WechatPaymentGateway(properties, CLOCK).prepay("x", new java.math.BigDecimal("1.00"), "订单")).isInstanceOf(com.campushub.common.BusinessException.class);
        assertThatThrownBy(() -> new AlipayPaymentGateway(properties, CLOCK).prepay("x", new java.math.BigDecimal("1.00"), "订单")).isInstanceOf(com.campushub.common.BusinessException.class);
    }
    @Test void alipayQueryDateParsingDoesNotDependOnServerTimeZone() throws Exception {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            var parser = new com.alipay.api.internal.parser.json.ObjectJsonParser<>(com.alipay.api.response.AlipayTradeQueryResponse.class);
            var response = parser.parse("{\"alipay_trade_query_response\":{\"code\":\"10000\",\"trade_status\":\"TRADE_SUCCESS\",\"send_pay_date\":\"2026-10-31 12:00:00\"}}");
            assertThat(java.time.LocalDateTime.ofInstant(response.getSendPayDate().toInstant(), CLOCK.getZone())).isEqualTo(NOW);
        } finally { java.util.TimeZone.setDefault(original); }
    }
}
