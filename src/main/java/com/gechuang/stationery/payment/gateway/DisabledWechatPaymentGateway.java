package com.gechuang.stationery.payment.gateway;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;

public class DisabledWechatPaymentGateway implements WechatPaymentGateway {

    @Override
    public NativeOrder createNativeOrder(CreateNativeOrder command) {
        throw unavailable();
    }

    @Override
    public GatewayPayment queryByOutTradeNo(String outTradeNo, String subMchid) {
        throw unavailable();
    }

    @Override
    public void closeOrder(String outTradeNo, String subMchid) {
        throw unavailable();
    }

    @Override
    public GatewayPayment parseNotification(WechatNotification notification) {
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.BIZ_409, "微信真实支付尚未配置");
    }
}
