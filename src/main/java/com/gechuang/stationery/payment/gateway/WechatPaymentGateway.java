package com.gechuang.stationery.payment.gateway;

import java.math.BigDecimal;

public interface WechatPaymentGateway {

    boolean isAvailable();

    NativeOrder createNativeOrder(CreateNativeOrder command);

    GatewayPayment queryByOutTradeNo(String outTradeNo, String subMchid);

    void closeOrder(String outTradeNo, String subMchid);

    GatewayPayment parseNotification(WechatNotification notification);

    record CreateNativeOrder(String outTradeNo,
                             String subMchid,
                             String description,
                             BigDecimal amount,
                             String payerClientIp,
                             String storeId,
                             String storeName,
                             String storeAreaCode,
                             String storeAddress) {
    }

    record NativeOrder(String codeUrl) {
    }

    record GatewayPayment(String outTradeNo,
                          String transactionId,
                          String spAppId,
                          String spMchid,
                          String subMchid,
                          String tradeState,
                          String tradeStateDescription,
                          BigDecimal amount,
                          String successTime) {
    }

    record WechatNotification(String serialNumber,
                              String signature,
                              String timestamp,
                              String nonce,
                              String rawBody) {
    }
}
