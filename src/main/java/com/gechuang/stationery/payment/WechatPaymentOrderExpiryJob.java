package com.gechuang.stationery.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WechatPaymentOrderExpiryJob {

    private final WechatPaymentOrderService orderService;

    public WechatPaymentOrderExpiryJob(WechatPaymentOrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedRate = 60_000L)
    public void closeExpiredOrders() {
        orderService.closeExpiredOrders();
    }
}
