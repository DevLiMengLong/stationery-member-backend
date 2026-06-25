package com.gechuang.stationery.notification;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MemberNotificationServiceTest {

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
