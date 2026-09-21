package com.gechuang.stationery.payment;

public class InvalidWechatPaymentNotificationException extends RuntimeException {

    public InvalidWechatPaymentNotificationException(String message) {
        super(message);
    }
}
