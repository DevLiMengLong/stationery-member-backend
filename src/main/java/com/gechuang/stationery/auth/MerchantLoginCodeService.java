package com.gechuang.stationery.auth;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.mainflow.MainFlowMapper;
import com.gechuang.stationery.notification.MemberNotificationMessage;
import com.gechuang.stationery.notification.MemberNotificationProperties;
import com.gechuang.stationery.notification.MemberNotificationSender;
import com.gechuang.stationery.notification.MemberNotificationType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MerchantLoginCodeService {

    private static final String SCENE = "MERCHANT_LOGIN";
    private static final int CODE_LIFETIME_MINUTES = 5;
    private static final int CODE_COOLDOWN_SECONDS = 60;
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MainFlowMapper mapper;
    private final MemberNotificationSender sender;
    private final MemberNotificationProperties notificationProperties;
    private final String hmacKey;

    public MerchantLoginCodeService(MainFlowMapper mapper,
                                    MemberNotificationSender sender,
                                    MemberNotificationProperties notificationProperties,
                                    @org.springframework.beans.factory.annotation.Value(
                                            "${stationery.auth.code-hmac-key:}") String hmacKey) {
        this.mapper = mapper;
        this.sender = sender;
        this.notificationProperties = notificationProperties;
        this.hmacKey = hmacKey == null ? "" : hmacKey.trim();
    }

    @Transactional(rollbackFor = Exception.class)
    public void sendLoginCode(String mobile) {
        String normalizedMobile = validateMobile(mobile);
        requireSmsConfiguration();

        LocalDateTime now = LocalDateTime.now();
        if (mapper.lockStoreLoginMobile(normalizedMobile) == null
                || mapper.selectStoresByLoginMobile(normalizedMobile, now).isEmpty()) {
            return;
        }
        if (mapper.countRecentVerificationCodes(normalizedMobile, SCENE,
                now.minusSeconds(CODE_COOLDOWN_SECONDS)) > 0) {
            throw new BusinessException(ErrorCode.BIZ_409, "验证码请求过于频繁，请稍后再试");
        }

        mapper.supersedeUnusedVerificationCodes(normalizedMobile, SCENE);
        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
        mapper.insertVerificationCode(normalizedMobile, SCENE, codeDigest(normalizedMobile, code),
                now.plusMinutes(CODE_LIFETIME_MINUTES));

        MemberNotificationMessage message = new MemberNotificationMessage(
                MemberNotificationType.MERCHANT_LOGIN_CODE,
                normalizedMobile,
                null,
                "文具店会员登录验证码：" + code + "，5分钟内有效。如非本人操作，请忽略。",
                Map.of("code", code, "minutes", String.valueOf(CODE_LIFETIME_MINUTES)),
                List.of(code, String.valueOf(CODE_LIFETIME_MINUTES))
        );
        try {
            if (!sender.send(message)) {
                throw new IllegalStateException("SMS provider did not accept merchant login code");
            }
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.BIZ_409, "验证码发送失败，请稍后重试");
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyLoginCode(String mobile, String code) {
        String normalizedMobile = validateMobile(mobile);
        if (!StringUtils.hasText(code) || !code.trim().matches("\\d{6}")) {
            throw invalidCode();
        }
        requireHmacKey();

        Map<String, Object> latest = mapper.selectLatestVerificationCodeForUpdate(normalizedMobile, SCENE);
        if (latest == null || !"UNUSED".equals(text(latest.get("status")))) {
            throw invalidCode();
        }

        Long id = longValue(latest.get("id"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = dateTime(latest.get("expiresAt"));
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            mapper.markVerificationCodeExpired(id);
            throw invalidCode();
        }

        String storedDigest = text(latest.get("code"));
        String submittedDigest = codeDigest(normalizedMobile, code.trim());
        if (!constantTimeEquals(storedDigest, submittedDigest)) {
            int attempts = integerValue(latest.get("attemptCount")) + 1;
            mapper.incrementVerificationCodeAttempts(id);
            if (attempts >= MAX_CODE_ATTEMPTS) {
                mapper.lockVerificationCode(id);
            }
            throw invalidCode();
        }

        if (mapper.markVerificationCodeUsed(id) != 1) {
            throw invalidCode();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String createStoreSelectionToken(String mobile) {
        String normalizedMobile = validateMobile(mobile);
        LocalDateTime now = LocalDateTime.now();
        mapper.deleteExpiredMerchantLoginChallenges(now);
        String token = UUID.randomUUID().toString().replace("-", "");
        mapper.insertMerchantLoginChallenge(token, normalizedMobile, now.plusMinutes(CODE_LIFETIME_MINUTES));
        return token;
    }

    @Transactional(rollbackFor = Exception.class)
    public String createPasswordStoreSelectionToken(String mobile) {
        String normalizedMobile = validateMobile(mobile);
        LocalDateTime now = LocalDateTime.now();
        mapper.deleteExpiredMerchantLoginChallenges(now);
        String token = UUID.randomUUID().toString().replace("-", "");
        mapper.insertMerchantPasswordLoginChallenge(token, normalizedMobile,
                now.plusMinutes(CODE_LIFETIME_MINUTES));
        return token;
    }

    @Transactional(rollbackFor = Exception.class)
    public String consumeStoreSelectionToken(String token) {
        StoreSelectionChallenge challenge = consumeStoreSelectionChallenge(token);
        if (!"SMS".equals(challenge.authMethod())) {
            throw invalidCode();
        }
        return challenge.mobile();
    }

    @Transactional(rollbackFor = Exception.class)
    public StoreSelectionChallenge consumeStoreSelectionChallenge(String token) {
        if (!StringUtils.hasText(token)) {
            throw invalidCode();
        }
        Map<String, Object> challenge = mapper.selectMerchantLoginChallengeForUpdate(token.trim(), LocalDateTime.now());
        if (challenge == null || mapper.deleteMerchantLoginChallenge(longValue(challenge.get("id"))) != 1) {
            throw invalidCode();
        }
        String authMethod = text(challenge.get("authMethod"));
        if (!List.of("SMS", "PASSWORD").contains(authMethod)) {
            throw invalidCode();
        }
        return new StoreSelectionChallenge(validateMobile(text(challenge.get("mobile"))), authMethod);
    }

    public record StoreSelectionChallenge(String mobile, String authMethod) {}

    private void requireSmsConfiguration() {
        if (!notificationProperties.isEnabled() || hmacKey.length() < 32) {
            throw smsUnavailable();
        }
        String channel = notificationProperties.getChannel();
        if ("tencent-cloud-sms".equalsIgnoreCase(channel)) {
            MemberNotificationProperties.TencentSms sms = notificationProperties.getTencentSms();
            if (!StringUtils.hasText(sms.getMerchantLoginTemplateId())) {
                throw smsUnavailable();
            }
            return;
        }
        if ("aliyun-sms".equalsIgnoreCase(channel)) {
            MemberNotificationProperties.AliyunSms sms = notificationProperties.getAliyunSms();
            if (!StringUtils.hasText(sms.getMerchantLoginTemplateCode())
                    || configuredParameterCount(sms.getMerchantLoginParamNames()) != 2) {
                throw smsUnavailable();
            }
            return;
        }
        throw smsUnavailable();
    }

    private void requireHmacKey() {
        if (hmacKey.length() < 32) {
            throw smsUnavailable();
        }
    }

    private BusinessException smsUnavailable() {
        return new BusinessException(ErrorCode.BIZ_409, "手机号登录短信服务尚未配置");
    }

    private BusinessException invalidCode() {
        return new BusinessException(ErrorCode.AUTH_401, "手机号或验证码错误，请重新获取验证码");
    }

    private int configuredParameterCount(String names) {
        if (!StringUtils.hasText(names)) {
            return 0;
        }
        return (int) java.util.Arrays.stream(names.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .count();
    }

    private String validateMobile(String mobile) {
        String normalized = mobile == null ? "" : mobile.trim();
        if (!normalized.matches("1[3-9]\\d{9}")) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "手机号格式不正确");
        }
        return normalized;
    }

    private String codeDigest(String mobile, String code) {
        requireHmacKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((mobile + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (!StringUtils.hasText(text(value))) {
            return null;
        }
        return LocalDateTime.parse(text(value).replace(' ', 'T'));
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private int integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
