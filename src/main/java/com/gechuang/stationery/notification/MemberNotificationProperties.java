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
    private AliyunSms aliyunSms = new AliyunSms();

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

    public AliyunSms getAliyunSms() {
        return aliyunSms;
    }

    public void setAliyunSms(AliyunSms aliyunSms) {
        this.aliyunSms = aliyunSms;
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

    public static class AliyunSms {
        private String endpoint = "dysmsapi.aliyuncs.com";
        private String signName;
        private String memberCreatedTemplateCode;
        private String rechargeTemplateCode;
        private String consumptionTemplateCode;
        private String memberCreatedParamNames = "business_code,member_code";
        private String rechargeParamNames = "business_code,input,input_gift";
        private String consumptionParamNames = "business_code,output,left";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getMemberCreatedTemplateCode() {
            return memberCreatedTemplateCode;
        }

        public void setMemberCreatedTemplateCode(String memberCreatedTemplateCode) {
            this.memberCreatedTemplateCode = memberCreatedTemplateCode;
        }

        public String getRechargeTemplateCode() {
            return rechargeTemplateCode;
        }

        public void setRechargeTemplateCode(String rechargeTemplateCode) {
            this.rechargeTemplateCode = rechargeTemplateCode;
        }

        public String getConsumptionTemplateCode() {
            return consumptionTemplateCode;
        }

        public void setConsumptionTemplateCode(String consumptionTemplateCode) {
            this.consumptionTemplateCode = consumptionTemplateCode;
        }

        public String getMemberCreatedParamNames() {
            return memberCreatedParamNames;
        }

        public void setMemberCreatedParamNames(String memberCreatedParamNames) {
            this.memberCreatedParamNames = memberCreatedParamNames;
        }

        public String getRechargeParamNames() {
            return rechargeParamNames;
        }

        public void setRechargeParamNames(String rechargeParamNames) {
            this.rechargeParamNames = rechargeParamNames;
        }

        public String getConsumptionParamNames() {
            return consumptionParamNames;
        }

        public void setConsumptionParamNames(String consumptionParamNames) {
            this.consumptionParamNames = consumptionParamNames;
        }
    }
}
