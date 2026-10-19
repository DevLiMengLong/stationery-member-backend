package com.gechuang.stationery.notification;

@FunctionalInterface
public interface MemberNotificationSender {

    boolean send(MemberNotificationMessage message);
}
