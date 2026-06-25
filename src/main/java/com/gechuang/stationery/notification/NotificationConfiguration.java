package com.gechuang.stationery.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MemberNotificationProperties.class)
public class NotificationConfiguration {
}
