package com.gechuang.stationery.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.mainflow.MainFlowMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MemberNotificationSmsQuotaTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final SmsQuotaService quotaService = new SmsQuotaService(mapper);

    @Test
    void smsNotificationShouldReserveOneAvailableSmsBeforeSending() {
        when(mapper.tryConsumeSmsQuota(5L)).thenReturn(1);
        AtomicInteger sendCount = new AtomicInteger();
        MemberNotificationService service = service("aliyun-sms", message -> {
            sendCount.incrementAndGet();
            return true;
        });

        service.notifyMemberCreated(
                Map.of("id", 5L, "shopName", "测试门店"),
                Map.of("mobile", "13800000073", "memberNo", "M-73")
        );

        assertThat(sendCount).hasValue(1);
        verify(mapper).tryConsumeSmsQuota(5L);
    }

    @Test
    void smsNotificationShouldNotSendWhenMerchantHasNoAvailableSms() {
        when(mapper.tryConsumeSmsQuota(5L)).thenReturn(0);
        AtomicInteger sendCount = new AtomicInteger();
        MemberNotificationService service = service("tencent-cloud-sms", message -> {
            sendCount.incrementAndGet();
            return true;
        });

        service.notifyMemberCreated(
                Map.of("id", 5L, "shopName", "测试门店"),
                Map.of("mobile", "13800000073", "memberNo", "M-73")
        );

        assertThat(sendCount).hasValue(0);
        verify(mapper).tryConsumeSmsQuota(5L);
    }

    @Test
    void providerRejectionShouldReleaseReservedSms() {
        when(mapper.tryConsumeSmsQuota(5L)).thenReturn(1);
        MemberNotificationService service = service("aliyun-sms", message -> false);

        service.notifyMemberCreated(
                Map.of("id", 5L, "shopName", "测试门店"),
                Map.of("mobile", "13800000073", "memberNo", "M-73")
        );

        verify(mapper).restoreSmsQuota(5L);
    }

    @Test
    void quotaLookupFailureShouldSkipSmsWithoutBreakingBusinessFlow() {
        when(mapper.tryConsumeSmsQuota(5L)).thenThrow(new IllegalStateException("database unavailable"));
        AtomicInteger sendCount = new AtomicInteger();
        MemberNotificationService service = service("aliyun-sms", message -> {
            sendCount.incrementAndGet();
            return true;
        });

        assertThatCode(() -> service.notifyMemberCreated(
                Map.of("id", 5L, "shopName", "测试门店"),
                Map.of("mobile", "13800000073", "memberNo", "M-73")
        )).doesNotThrowAnyException();

        assertThat(sendCount).hasValue(0);
    }

    @Test
    void nonSmsNotificationsShouldNotUseMerchantSmsQuota() {
        MemberNotificationService service = service("log", message -> true);

        service.notifyMemberCreated(
                Map.of("id", 5L, "shopName", "测试门店"),
                Map.of("mobile", "13800000073", "memberNo", "M-73")
        );

        verify(mapper, never()).tryConsumeSmsQuota(5L);
    }

    private MemberNotificationService service(String channel, MemberNotificationSender sender) {
        MemberNotificationProperties properties = new MemberNotificationProperties();
        properties.setChannel(channel);
        return new MemberNotificationService(new MemberNotificationMessageFactory(), sender, properties, quotaService);
    }
}
