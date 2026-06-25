package com.gechuang.stationery.notification;

@FunctionalInterface
public interface MemberNotificationSender {

    void send(MemberNotificationMessage message);
}
