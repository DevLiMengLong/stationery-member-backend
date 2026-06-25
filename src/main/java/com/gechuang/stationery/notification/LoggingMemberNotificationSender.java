package com.gechuang.stationery.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "stationery.notification.channel", havingValue = "log", matchIfMissing = true)
public class LoggingMemberNotificationSender implements MemberNotificationSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMemberNotificationSender.class);

    @Override
    public void send(MemberNotificationMessage message) {
        LOGGER.info("Member notification [{}] to mobile [{}]: {}",
                message.type(), message.recipientMobile(), message.content());
    }
}
