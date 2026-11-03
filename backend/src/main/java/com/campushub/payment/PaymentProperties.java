package com.campushub.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 环境变量绑定的商户配置，不包含测试假密钥，不把私钥写入仓库或返回客户端。 */
@Component
@ConfigurationProperties(prefix = "campushub.payment")
@Getter @Setter
public class PaymentProperties {
    /** 在创建任何支付单之前拒绝不完整配置，避免把空商户身份冻结到支付记录。 */
    @jakarta.annotation.PostConstruct
    public void validateEnabledChannels() {
        if (wechat.isEnabled()) {
            require(wechat.getAppId(), "CAMPUSHUB_WECHAT_APP_ID");
            require(wechat.getMerchantId(), "CAMPUSHUB_WECHAT_MERCHANT_ID");
            require(wechat.getMerchantSerialNumber(), "CAMPUSHUB_WECHAT_SERIAL");
            require(wechat.getPublicKeyId(), "CAMPUSHUB_WECHAT_PUBLIC_KEY_ID");
            requireFile(wechat.getPrivateKeyPath(), "CAMPUSHUB_WECHAT_PRIVATE_KEY_PATH");
            requireFile(wechat.getPublicKeyPath(), "CAMPUSHUB_WECHAT_PUBLIC_KEY_PATH");
            require(wechat.getApiV3Key(), "CAMPUSHUB_WECHAT_API_V3_KEY");
            if (wechat.getApiV3Key().getBytes(java.nio.charset.StandardCharsets.UTF_8).length != 32)
                throw new IllegalStateException("微信APIv3密钥必须为32字节");
            requireHttps(wechat.getNotifyUrl(), "wechat");
        }
        if (alipay.isEnabled()) {
            require(alipay.getAppId(), "CAMPUSHUB_ALIPAY_APP_ID");
            require(alipay.getSellerId(), "CAMPUSHUB_ALIPAY_SELLER_ID");
            requireFile(alipay.getPrivateKeyPath(), "CAMPUSHUB_ALIPAY_PRIVATE_KEY_PATH");
            requireFile(alipay.getPublicKeyPath(), "CAMPUSHUB_ALIPAY_PUBLIC_KEY_PATH");
            requireHttps(alipay.getNotifyUrl(), "alipay");
        }
    }
    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("启用支付前必须配置 " + name);
    }
    private void requireFile(String value, String name) {
        require(value, name);
        if (!java.nio.file.Files.isReadable(java.nio.file.Path.of(value))) throw new IllegalStateException("支付密钥文件不可读取：" + name);
    }
    private void requireHttps(String value, String channel) {
        require(value, channel + "回调地址");
        java.net.URI uri = java.net.URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getPath().endsWith("/v1/payments/notify/" + channel))
            throw new IllegalStateException("支付回调必须为HTTPS且对应本渠道通知路径");
    }
    private boolean manualSettlementEnabled = true;
    private Wechat wechat = new Wechat();
    private Alipay alipay = new Alipay();
    @Getter @Setter
    public static class Wechat {
        private boolean enabled;
        private String appId;
        private String merchantId;
        private String merchantSerialNumber;
        private String privateKeyPath;
        private String publicKeyPath;
        private String publicKeyId;
        private String apiV3Key;
        private String notifyUrl;
    }
    @Getter @Setter
    public static class Alipay {
        private boolean enabled;
        private boolean sandbox;
        private String appId;
        private String sellerId;
        private String privateKeyPath;
        private String publicKeyPath;
        private String notifyUrl;
    }
}
