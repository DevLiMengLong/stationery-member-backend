package com.gechuang.stationery.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "stationery.notification.channel", havingValue = "tencent-cloud-sms")
public class TencentCloudSmsNotificationSender implements MemberNotificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(TencentCloudSmsNotificationSender.class);
    private static final String ACTION = "SendSms";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String HTTP_REQUEST_METHOD = "POST";
    private static final String SERVICE = "sms";
    private static final String SIGNED_HEADERS = "content-type;host;x-tc-action";
    private static final String TC3_REQUEST = "tc3_request";
    private static final String VERSION = "2021-01-11";

    private final RestClient restClient;
    private final MemberNotificationProperties properties;
    private final ObjectMapper objectMapper;

    public TencentCloudSmsNotificationSender(RestClient.Builder restClientBuilder,
                                             MemberNotificationProperties properties,
                                             ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(MemberNotificationMessage message) {
        if (!StringUtils.hasText(message.recipientMobile())) {
            LOGGER.info("Skip SMS notification [{}] because member mobile is empty", message.type());
            return;
        }
        MemberNotificationProperties.TencentSms sms = properties.getTencentSms();
        String templateId = templateId(sms, message.type());
        if (!StringUtils.hasText(templateId)) {
            LOGGER.info("Skip SMS notification [{}] because Tencent SMS template id is empty", message.type());
            return;
        }
        validateConfig(sms);

        String payload = payload(sms, templateId, message);
        long timestamp = Instant.now().getEpochSecond();
        URI endpoint = URI.create(sms.getEndpoint());
        String body = restClient.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, authorization(sms, endpoint, payload, timestamp))
                .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
                .header(HttpHeaders.HOST, endpoint.getHost())
                .header("X-TC-Action", ACTION)
                .header("X-TC-Timestamp", String.valueOf(timestamp))
                .header("X-TC-Version", VERSION)
                .header("X-TC-Region", sms.getRegion())
                .body(payload)
                .retrieve()
                .body(String.class);
        if (!success(body)) {
            throw new IllegalStateException("Tencent Cloud SMS failed: " + body);
        }
    }

    private void validateConfig(MemberNotificationProperties.TencentSms sms) {
        if (!StringUtils.hasText(sms.getEndpoint())
                || !StringUtils.hasText(sms.getSecretId())
                || !StringUtils.hasText(sms.getSecretKey())
                || !StringUtils.hasText(sms.getSmsSdkAppId())
                || !StringUtils.hasText(sms.getSignName())) {
            throw new IllegalStateException("Tencent Cloud SMS config is incomplete");
        }
    }

    private String payload(MemberNotificationProperties.TencentSms sms,
                           String templateId,
                           MemberNotificationMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("PhoneNumberSet", List.of(normalizePhoneNumber(message.recipientMobile())));
        payload.put("SmsSdkAppId", sms.getSmsSdkAppId());
        payload.put("SignName", sms.getSignName());
        payload.put("TemplateId", templateId);
        payload.put("TemplateParamSet", message.smsTemplateParams());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Tencent Cloud SMS payload serialize failed", exception);
        }
    }

    private String authorization(MemberNotificationProperties.TencentSms sms,
                                 URI endpoint,
                                 String payload,
                                 long timestamp) {
        String host = endpoint.getHost();
        String canonicalUri = StringUtils.hasText(endpoint.getPath()) ? endpoint.getPath() : "/";
        String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + ACTION.toLowerCase(Locale.ROOT) + "\n";
        String canonicalRequest = HTTP_REQUEST_METHOD + "\n"
                + canonicalUri + "\n"
                + "" + "\n"
                + canonicalHeaders + "\n"
                + SIGNED_HEADERS + "\n"
                + sha256Hex(payload);

        String date = LocalDate.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        String credentialScope = date + "/" + SERVICE + "/" + TC3_REQUEST;
        String stringToSign = ALGORITHM + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        byte[] secretDate = hmacSha256(("TC3" + sms.getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, TC3_REQUEST);
        String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));
        return ALGORITHM
                + " Credential=" + sms.getSecretId() + "/" + credentialScope
                + ", SignedHeaders=" + SIGNED_HEADERS
                + ", Signature=" + signature;
    }

    private boolean success(String body) {
        Map<?, ?> root;
        try {
            root = objectMapper.readValue(body, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Tencent Cloud SMS response parse failed: " + body, exception);
        }
        Object response = root.get("Response");
        if (!(response instanceof Map<?, ?> responseMap) || responseMap.containsKey("Error")) {
            return false;
        }
        Object sendStatusSet = responseMap.get("SendStatusSet");
        if (!(sendStatusSet instanceof List<?> statuses) || statuses.isEmpty()) {
            return false;
        }
        return statuses.stream().allMatch(this::successStatus);
    }

    private boolean successStatus(Object status) {
        if (!(status instanceof Map<?, ?> statusMap)) {
            return false;
        }
        Object code = statusMap.get("Code");
        return "Ok".equals(code == null ? null : String.valueOf(code));
    }

    private String templateId(MemberNotificationProperties.TencentSms sms, MemberNotificationType type) {
        return switch (type) {
            case MEMBER_CREATED -> sms.getMemberCreatedTemplateId();
            case RECHARGE_SUCCEEDED -> sms.getRechargeTemplateId();
            case CONSUMPTION_SUCCEEDED -> sms.getConsumptionTemplateId();
        };
    }

    private String normalizePhoneNumber(String mobile) {
        String value = mobile.trim();
        if (value.startsWith("+")) {
            return value;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.startsWith("86") && digits.length() > 11) {
            return "+" + digits;
        }
        return "+86" + digits;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private byte[] hmacSha256(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 sign failed", exception);
        }
    }
}
