package com.gechuang.stationery.notification;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "stationery.notification.channel", havingValue = "wechat-official-account")
public class WechatOfficialAccountNotificationSender implements MemberNotificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatOfficialAccountNotificationSender.class);
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token"
            + "?grant_type=client_credential&appid={appId}&secret={appSecret}";
    private static final String SEND_TEMPLATE_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send"
            + "?access_token={accessToken}";
    private static final long TOKEN_EXPIRE_SKEW_SECONDS = 60L;

    private final RestClient restClient;
    private final MemberNotificationProperties properties;

    private String cachedAccessToken;
    private Instant cachedAccessTokenExpiresAt = Instant.EPOCH;

    public WechatOfficialAccountNotificationSender(RestClient.Builder restClientBuilder,
                                                   MemberNotificationProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public void send(MemberNotificationMessage message) {
        if (!StringUtils.hasText(message.recipientOpenid())) {
            LOGGER.info("Skip WeChat notification [{}] because member openid is empty", message.type());
            return;
        }
        String templateId = templateId(message.type());
        if (!StringUtils.hasText(templateId)) {
            LOGGER.info("Skip WeChat notification [{}] because template id is empty", message.type());
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touser", message.recipientOpenid());
        payload.put("template_id", templateId);
        payload.put("data", wechatData(message.templateData()));

        Map<?, ?> response = restClient.post()
                .uri(SEND_TEMPLATE_URL, accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);
        if (!success(response)) {
            throw new IllegalStateException("WeChat template message failed: " + response);
        }
    }

    private String accessToken() {
        if (StringUtils.hasText(cachedAccessToken) && Instant.now().isBefore(cachedAccessTokenExpiresAt)) {
            return cachedAccessToken;
        }
        MemberNotificationProperties.Wechat wechat = properties.getWechat();
        if (!StringUtils.hasText(wechat.getAppId()) || !StringUtils.hasText(wechat.getAppSecret())) {
            throw new IllegalStateException("WeChat app id or app secret is empty");
        }
        Map<?, ?> response = restClient.get()
                .uri(TOKEN_URL, wechat.getAppId(), wechat.getAppSecret())
                .retrieve()
                .body(Map.class);
        Object token = response == null ? null : response.get("access_token");
        Object expiresIn = response == null ? null : response.get("expires_in");
        if (!StringUtils.hasText(token == null ? null : String.valueOf(token))) {
            throw new IllegalStateException("WeChat access token failed: " + response);
        }
        long seconds = expiresIn instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(expiresIn));
        cachedAccessToken = String.valueOf(token);
        cachedAccessTokenExpiresAt = Instant.now().plusSeconds(Math.max(1, seconds - TOKEN_EXPIRE_SKEW_SECONDS));
        return cachedAccessToken;
    }

    private Map<String, Map<String, String>> wechatData(Map<String, String> templateData) {
        Map<String, Map<String, String>> data = new LinkedHashMap<>();
        templateData.forEach((key, value) -> data.put(key, Map.of("value", value)));
        return data;
    }

    private String templateId(MemberNotificationType type) {
        MemberNotificationProperties.Wechat wechat = properties.getWechat();
        return switch (type) {
            case MEMBER_CREATED -> wechat.getMemberCreatedTemplateId();
            case RECHARGE_SUCCEEDED -> wechat.getRechargeTemplateId();
            case CONSUMPTION_SUCCEEDED -> wechat.getConsumptionTemplateId();
        };
    }

    private boolean success(Map<?, ?> response) {
        if (response == null) {
            return false;
        }
        Object errcode = response.get("errcode");
        if (errcode == null) {
            return false;
        }
        if (errcode instanceof Number number) {
            return number.intValue() == 0;
        }
        return "0".equals(String.valueOf(errcode));
    }
}
