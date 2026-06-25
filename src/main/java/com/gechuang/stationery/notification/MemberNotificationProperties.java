package com.gechuang.stationery.notification;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stationery.notification")
public class MemberNotificationProperties {

    private boolean enabled = true;
    private String channel = "log";
    private Wechat wechat = new Wechat();
    private TencentSms tencentSms = new TencentSms();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Wechat getWechat() {
        return wechat;
    }

    public void setWechat(Wechat wechat) {
        this.wechat = wechat;
    }

    public TencentSms getTencentSms() {
        return tencentSms;
    }

    public void setTencentSms(TencentSms tencentSms) {
        this.tencentSms = tencentSms;
    }

    public static class Wechat {
        private String appId;
        private String appSecret;
        private String memberCreatedTemplateId;
        private String rechargeTemplateId;
        private String consumptionTemplateId;
        private Map<String, String> openidByMobile = new HashMap<>();

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getMemberCreatedTemplateId() {
            return memberCreatedTemplateId;
        }

        public void setMemberCreatedTemplateId(String memberCreatedTemplateId) {
            this.memberCreatedTemplateId = memberCreatedTemplateId;
        }

        public String getRechargeTemplateId() {
            return rechargeTemplateId;
        }

        public void setRechargeTemplateId(String rechargeTemplateId) {
            this.rechargeTemplateId = rechargeTemplateId;
        }

        public String getConsumptionTemplateId() {
            return consumptionTemplateId;
        }

        public void setConsumptionTemplateId(String consumptionTemplateId) {
            this.consumptionTemplateId = consumptionTemplateId;
        }

        public Map<String, String> getOpenidByMobile() {
            return openidByMobile;
        }

        public void setOpenidByMobile(Map<String, String> openidByMobile) {
            this.openidByMobile = openidByMobile;
        }
    }

    public static class TencentSms {
        private String endpoint = "https://sms.tencentcloudapi.com/";
        private String region = "ap-guangzhou";
        private String secretId;
        private String secretKey;
        private String smsSdkAppId;
        private String signName;
        private String memberCreatedTemplateId;
        private String rechargeTemplateId;
        private String consumptionTemplateId;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(String secretId) {
            this.secretId = secretId;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getSmsSdkAppId() {
            return smsSdkAppId;
        }

        public void setSmsSdkAppId(String smsSdkAppId) {
            this.smsSdkAppId = smsSdkAppId;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getMemberCreatedTemplateId() {
            return memberCreatedTemplateId;
        }

        public void setMemberCreatedTemplateId(String memberCreatedTemplateId) {
            this.memberCreatedTemplateId = memberCreatedTemplateId;
        }

        public String getRechargeTemplateId() {
            return rechargeTemplateId;
        }

        public void setRechargeTemplateId(String rechargeTemplateId) {
            this.rechargeTemplateId = rechargeTemplateId;
        }

        public String getConsumptionTemplateId() {
            return consumptionTemplateId;
        }

        public void setConsumptionTemplateId(String consumptionTemplateId) {
            this.consumptionTemplateId = consumptionTemplateId;
        }
    }
}
