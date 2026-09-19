package com.gechuang.stationery.notification;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "stationery.notification.channel", havingValue = "aliyun-sms")
public class AliyunSmsNotificationSender implements MemberNotificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(AliyunSmsNotificationSender.class);
    private static final String DEFAULT_ENDPOINT = "dysmsapi.aliyuncs.com";

    private final MemberNotificationProperties properties;
    private final ObjectMapper objectMapper;
    private volatile Client client;

    public AliyunSmsNotificationSender(MemberNotificationProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null);
    }

    AliyunSmsNotificationSender(MemberNotificationProperties properties,
                                ObjectMapper objectMapper,
                                Client client) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    @Override
    public void send(MemberNotificationMessage message) {
        if (!StringUtils.hasText(message.recipientMobile())) {
            LOGGER.info("Skip Alibaba Cloud SMS notification [{}] because member mobile is empty", message.type());
            return;
        }

        MemberNotificationProperties.AliyunSms sms = properties.getAliyunSms();
        String templateCode = templateCode(sms, message.type());
        if (!StringUtils.hasText(templateCode)) {
            LOGGER.info("Skip Alibaba Cloud SMS notification [{}] because template code is empty", message.type());
            return;
        }
        if (!StringUtils.hasText(sms.getSignName())) {
            throw new IllegalStateException("Alibaba Cloud SMS sign name is empty");
        }

        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(message.recipientMobile().trim())
                .setSignName(sms.getSignName().trim())
                .setTemplateCode(templateCode.trim())
                .setTemplateParam(templateParam(sms, message));
        try {
            SendSmsResponse response = client().sendSmsWithOptions(request, new RuntimeOptions());
            SendSmsResponseBody body = response == null ? null : response.getBody();
            if (body == null || !"OK".equalsIgnoreCase(body.getCode())) {
                String detail = body == null ? "empty response" : body.getMessage();
                throw new IllegalStateException("Alibaba Cloud SMS failed: " + detail);
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Alibaba Cloud SMS request failed", exception);
        }
    }

    private Client client() {
        Client value = client;
        if (value != null) {
            return value;
        }
        synchronized (this) {
            if (client == null) {
                client = createClient();
            }
            return client;
        }
    }

    private Client createClient() {
        try {
            com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();
            Config config = new Config().setCredential(credential);
            String endpoint = properties.getAliyunSms().getEndpoint();
            config.setEndpoint(StringUtils.hasText(endpoint) ? endpoint.trim() : DEFAULT_ENDPOINT);
            return new Client(config);
        } catch (Exception exception) {
            throw new IllegalStateException("Alibaba Cloud SMS client initialization failed", exception);
        }
    }

    private String templateCode(MemberNotificationProperties.AliyunSms sms, MemberNotificationType type) {
        return switch (type) {
            case MEMBER_CREATED -> sms.getMemberCreatedTemplateCode();
            case RECHARGE_SUCCEEDED -> sms.getRechargeTemplateCode();
            case CONSUMPTION_SUCCEEDED -> sms.getConsumptionTemplateCode();
        };
    }

    private String templateParam(MemberNotificationProperties.AliyunSms sms,
                                 MemberNotificationMessage message) {
        List<String> values = message.smsTemplateParams();
        List<String> names = paramNames(sms, message.type());
        if (names.isEmpty()) {
            return null;
        }
        if (values.size() != names.size()) {
            throw new IllegalStateException("Alibaba Cloud SMS template parameter count mismatch for "
                    + message.type() + ": expected " + names.size() + ", actual " + values.size());
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            params.put(names.get(i), values.get(i));
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Alibaba Cloud SMS template parameters serialize failed", exception);
        }
    }

    private List<String> paramNames(MemberNotificationProperties.AliyunSms sms, MemberNotificationType type) {
        String configured = switch (type) {
            case MEMBER_CREATED -> sms.getMemberCreatedParamNames();
            case RECHARGE_SUCCEEDED -> sms.getRechargeParamNames();
            case CONSUMPTION_SUCCEEDED -> sms.getConsumptionParamNames();
        };
        if (!StringUtils.hasText(configured)) {
            return List.of();
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
