package com.gechuang.stationery.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemberNotificationServiceTest {

    @Test
    void memberCreatedNotificationShouldCarryMemberNumberForSmsTemplate() {
        MemberNotificationProperties properties = new MemberNotificationProperties();
        AtomicReference<MemberNotificationMessage> captured = new AtomicReference<>();
        MemberNotificationService service = new MemberNotificationService(
                new MemberNotificationMessageFactory(),
                captured::set,
                properties
        );

        service.notifyMemberCreated(
                Map.of("shopName", "晨光文具店"),
                Map.of("mobile", "13800000073", "memberNo", "M202609190073")
        );

        assertThat(captured.get().smsTemplateParams())
                .containsExactly("晨光文具店", "M202609190073");
    }

    @Test
    void notificationFailureShouldNotBreakBusinessFlow() {
        MemberNotificationProperties properties = new MemberNotificationProperties();
        MemberNotificationService service = new MemberNotificationService(
                new MemberNotificationMessageFactory(),
                message -> {
                    throw new IllegalStateException("provider unavailable");
                },
                properties
        );

        assertThatCode(() -> service.notifyMemberCreated(
                Map.of("shopName", "晨光文具店"),
                Map.of("mobile", "13800000073")
        )).doesNotThrowAnyException();
    }
}
