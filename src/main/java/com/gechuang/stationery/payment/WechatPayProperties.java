package com.gechuang.stationery.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "stationery.payment")
public class WechatPayProperties {

    private String mode = PlatformPaymentMode.BOOKKEEPING.name();
    private final Wechat wechat = new Wechat();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Wechat getWechat() {
        return wechat;
    }

    public PlatformPaymentMode platformMode() {
        try {
            return PlatformPaymentMode.valueOf(mode == null ? "" : mode.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return PlatformPaymentMode.BOOKKEEPING;
        }
    }

    public boolean hasCompleteWechatConfiguration() {
        return StringUtils.hasText(wechat.spAppId)
                && StringUtils.hasText(wechat.spMchid)
                && StringUtils.hasText(wechat.merchantSerialNumber)
                && StringUtils.hasText(wechat.privateKeyPath)
                && StringUtils.hasText(wechat.apiV3Key)
                && StringUtils.hasText(wechat.publicKeyId)
                && StringUtils.hasText(wechat.publicKeyPath)
                && StringUtils.hasText(wechat.notifyUrl);
    }

    public static class Wechat {
        private String spAppId;
        private String spMchid;
        private String merchantSerialNumber;
        private String privateKeyPath;
        private String apiV3Key;
        private String publicKeyId;
        private String publicKeyPath;
        private String notifyUrl;

        public String getSpAppId() {
            return spAppId;
        }

        public void setSpAppId(String spAppId) {
            this.spAppId = spAppId;
        }

        public String getSpMchid() {
            return spMchid;
        }

        public void setSpMchid(String spMchid) {
            this.spMchid = spMchid;
        }

        public String getMerchantSerialNumber() {
            return merchantSerialNumber;
        }

        public void setMerchantSerialNumber(String merchantSerialNumber) {
            this.merchantSerialNumber = merchantSerialNumber;
        }

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }

        public String getApiV3Key() {
            return apiV3Key;
        }

        public void setApiV3Key(String apiV3Key) {
            this.apiV3Key = apiV3Key;
        }

        public String getPublicKeyId() {
            return publicKeyId;
        }

        public void setPublicKeyId(String publicKeyId) {
            this.publicKeyId = publicKeyId;
        }

        public String getPublicKeyPath() {
            return publicKeyPath;
        }

        public void setPublicKeyPath(String publicKeyPath) {
            this.publicKeyPath = publicKeyPath;
        }

        public String getNotifyUrl() {
            return notifyUrl;
        }

        public void setNotifyUrl(String notifyUrl) {
            this.notifyUrl = notifyUrl;
        }
    }
}
