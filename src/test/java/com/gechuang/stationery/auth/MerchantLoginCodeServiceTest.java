package com.gechuang.stationery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.mainflow.MainFlowMapper;
import com.gechuang.stationery.notification.MemberNotificationMessage;
import com.gechuang.stationery.notification.MemberNotificationSender;
import com.gechuang.stationery.notification.MemberNotificationType;
import com.gechuang.stationery.notification.MemberNotificationProperties;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MerchantLoginCodeServiceTest {

    private static final String MOBILE = "13800138000";

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MemberNotificationSender sender = mock(MemberNotificationSender.class);
    private final MemberNotificationProperties properties = new MemberNotificationProperties();
    private final MerchantLoginCodeService service =
            new MerchantLoginCodeService(mapper, sender, properties, "unit-test-hmac-key-at-least-32-characters");

    @BeforeEach
    void configureSms() {
        properties.setChannel("tencent-cloud-sms");
        properties.getTencentSms().setMerchantLoginTemplateId("login-template");
    }

    @Test
    void sendsShortLivedCodeOnlyToAnEligibleBoundPhoneAndStoresItsDigest() {
        when(mapper.lockStoreLoginMobile(MOBILE)).thenReturn(7L);
        when(mapper.selectStoresByLoginMobile(eq(MOBILE), any(LocalDateTime.class)))
                .thenReturn(List.of(Map.of("id", 7L)));
        when(mapper.countRecentVerificationCodes(eq(MOBILE), eq("MERCHANT_LOGIN"), any(LocalDateTime.class)))
                .thenReturn(0);
        when(sender.send(any(MemberNotificationMessage.class))).thenReturn(true);

        service.sendLoginCode(MOBILE);

        ArgumentCaptor<MemberNotificationMessage> message = ArgumentCaptor.forClass(MemberNotificationMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().type()).isEqualTo(MemberNotificationType.MERCHANT_LOGIN_CODE);
        assertThat(message.getValue().recipientMobile()).isEqualTo(MOBILE);
        assertThat(message.getValue().smsTemplateParams()).hasSize(2);
        assertThat(message.getValue().smsTemplateParams().get(0)).matches("\\d{6}");
        assertThat(message.getValue().smsTemplateParams().get(1)).isEqualTo("5");

        ArgumentCaptor<String> storedCode = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).insertVerificationCode(eq(MOBILE), eq("MERCHANT_LOGIN"), storedCode.capture(), expiry.capture());
        assertThat(storedCode.getValue()).hasSize(64).isNotEqualTo(message.getValue().smsTemplateParams().get(0));
        assertThat(expiry.getValue()).isAfter(LocalDateTime.now()).isBefore(LocalDateTime.now().plusMinutes(6));
    }

    @Test
    void doesNotSendLoginCodeToAnUnboundPhone() {
        when(mapper.selectStoresByLoginMobile(eq(MOBILE), any(LocalDateTime.class))).thenReturn(List.of());

        service.sendLoginCode(MOBILE);

        verify(sender, never()).send(any(MemberNotificationMessage.class));
        verify(mapper, never()).insertVerificationCode(any(), any(), any(), any());
    }

    @Test
    void enforcesThePerMobileCooldownBeforeSendingAnotherCode() {
        when(mapper.lockStoreLoginMobile(MOBILE)).thenReturn(7L);
        when(mapper.selectStoresByLoginMobile(eq(MOBILE), any(LocalDateTime.class)))
                .thenReturn(List.of(Map.of("id", "7")));
        when(mapper.countRecentVerificationCodes(eq(MOBILE), eq("MERCHANT_LOGIN"), any(LocalDateTime.class)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.sendLoginCode(MOBILE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码请求过于频繁，请稍后再试");

        verify(sender, never()).send(any(MemberNotificationMessage.class));
        verify(mapper, never()).insertVerificationCode(any(), any(), any(), any());
    }

    @Test
    void rejectsLoginCodeSendingWhenOnlyTheDevelopmentLoggerIsConfigured() {
        properties.setChannel("log");

        assertThatThrownBy(() -> service.sendLoginCode(MOBILE))
                .isInstanceOf(BusinessException.class);

        verify(sender, never()).send(any(MemberNotificationMessage.class));
    }

    @Test
    void consumesAValidLoginCodeOnce() {
        when(mapper.lockStoreLoginMobile(MOBILE)).thenReturn(7L);
        when(mapper.selectStoresByLoginMobile(eq(MOBILE), any(LocalDateTime.class)))
                .thenReturn(List.of(Map.of("id", 7L)));
        when(mapper.countRecentVerificationCodes(eq(MOBILE), eq("MERCHANT_LOGIN"), any(LocalDateTime.class)))
                .thenReturn(0);
        when(sender.send(any(MemberNotificationMessage.class))).thenReturn(true);
        service.sendLoginCode(MOBILE);

        ArgumentCaptor<MemberNotificationMessage> message = ArgumentCaptor.forClass(MemberNotificationMessage.class);
        verify(sender).send(message.capture());
        ArgumentCaptor<String> storedCode = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertVerificationCode(eq(MOBILE), eq("MERCHANT_LOGIN"), storedCode.capture(), any(LocalDateTime.class));
        when(mapper.selectLatestVerificationCodeForUpdate(eq(MOBILE), eq("MERCHANT_LOGIN")))
                .thenReturn(Map.of("id", 51L, "code", storedCode.getValue(), "status", "UNUSED",
                        "attemptCount", 0, "expiresAt", Timestamp.valueOf(LocalDateTime.now().plusMinutes(2))));
        when(mapper.markVerificationCodeUsed(51L)).thenReturn(1);

        service.verifyLoginCode(MOBILE, message.getValue().smsTemplateParams().get(0));

        verify(mapper).markVerificationCodeUsed(51L);
    }

    @Test
    void locksTheLoginCodeAfterFiveIncorrectAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        when(mapper.selectLatestVerificationCodeForUpdate(eq(MOBILE), eq("MERCHANT_LOGIN")))
                .thenAnswer(invocation -> Map.of(
                        "id", 52L,
                        "code", "not-the-login-code",
                        "status", "UNUSED",
                        "attemptCount", attempts.getAndIncrement(),
                        "expiresAt", Timestamp.valueOf(LocalDateTime.now().plusMinutes(2))));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verifyLoginCode(MOBILE, "000000"))
                    .isInstanceOf(BusinessException.class);
        }

        verify(mapper, times(5)).incrementVerificationCodeAttempts(52L);
        verify(mapper).lockVerificationCode(52L);
    }

    @Test
    void storeSelectionTicketIsSingleUseAndDoesNotCreateASession() {
        when(mapper.selectMerchantLoginChallengeForUpdate(eq("selection-token"), any(LocalDateTime.class)))
                .thenReturn(Map.of("id", 12L, "mobile", MOBILE, "authMethod", "SMS"));
        when(mapper.deleteMerchantLoginChallenge(12L)).thenReturn(1);

        String mobile = service.consumeStoreSelectionToken("selection-token");

        assertThat(mobile).isEqualTo(MOBILE);
        verify(mapper).deleteMerchantLoginChallenge(12L);
    }
}
