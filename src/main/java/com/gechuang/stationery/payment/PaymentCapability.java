package com.gechuang.stationery.payment;

public record PaymentCapability(PlatformPaymentMode platformMode,
                                PaymentMode configuredStoreMode,
                                PaymentMode effectiveMode,
                                String authorizationStatus,
                                boolean realPaymentAvailable) {
}
