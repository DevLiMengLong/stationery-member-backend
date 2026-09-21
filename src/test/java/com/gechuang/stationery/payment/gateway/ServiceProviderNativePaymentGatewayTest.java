package com.gechuang.stationery.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.payment.WechatPayProperties;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.partnerpayments.nativepay.NativePayService;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.partnerpayments.nativepay.model.Transaction;
import com.wechat.pay.java.service.partnerpayments.model.TransactionAmount;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ServiceProviderNativePaymentGatewayTest {

    private final NativePayService nativePayService = mock(NativePayService.class);
    private final NotificationParser notificationParser = mock(NotificationParser.class);
    private final WechatPayProperties properties = properties();
    private ServiceProviderNativePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new ServiceProviderNativePaymentGateway(nativePayService, notificationParser, properties);
    }

    @Test
    void createsPartnerNativeOrderWithExactFenAndStoreScene() {
        PrepayResponse response = new PrepayResponse();
        response.setCodeUrl("weixin://wxpay/example");
        when(nativePayService.prepay(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        WechatPaymentGateway.NativeOrder result = gateway.createNativeOrder(
                new WechatPaymentGateway.CreateNativeOrder(
                        "ORDER-1", "1900000002", "会员充值", new BigDecimal("12.34"),
                        "127.0.0.1", "STORE-1", "测试门店", "440300", "深圳市南山区"));

        assertThat(result.codeUrl()).isEqualTo("weixin://wxpay/example");
        ArgumentCaptor<PrepayRequest> captor = ArgumentCaptor.forClass(PrepayRequest.class);
        verify(nativePayService).prepay(captor.capture());
        PrepayRequest request = captor.getValue();
        assertThat(request.getSpAppid()).isEqualTo("wx-service-provider-app");
        assertThat(request.getSpMchid()).isEqualTo("1900000001");
        assertThat(request.getSubMchid()).isEqualTo("1900000002");
        assertThat(request.getOutTradeNo()).isEqualTo("ORDER-1");
        assertThat(request.getNotifyUrl()).isEqualTo("https://api.example.com/api/payments/wechat/notify");
        assertThat(request.getAmount().getTotal()).isEqualTo(1234);
        assertThat(request.getSceneInfo().getStoreInfo().getId()).isEqualTo("STORE-1");
        assertThat(request.getSceneInfo().getStoreInfo().getName()).isEqualTo("测试门店");
    }

    @Test
    void rejectsAmountThatCannotBeRepresentedInFen() {
        assertThatThrownBy(() -> gateway.createNativeOrder(
                new WechatPaymentGateway.CreateNativeOrder(
                        "ORDER-1", "1900000002", "会员充值", new BigDecimal("0.001"),
                        "127.0.0.1", "STORE-1", "测试门店", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分");
    }

    @Test
    void parsesNotificationFromRawBodyAndWechatHeaders() {
        Transaction transaction = transaction();
        when(notificationParser.parse(org.mockito.ArgumentMatchers.any(), eq(Transaction.class)))
                .thenReturn(transaction);

        WechatPaymentGateway.GatewayPayment result = gateway.parseNotification(
                new WechatPaymentGateway.WechatNotification(
                        "PUB_KEY_ID", "signature", "1700000000", "nonce", "{\"resource\":{}}"));

        assertThat(result.outTradeNo()).isEqualTo("ORDER-1");
        assertThat(result.amount()).isEqualByComparingTo("12.34");
        assertThat(result.tradeState()).isEqualTo("SUCCESS");
        ArgumentCaptor<RequestParam> captor = ArgumentCaptor.forClass(RequestParam.class);
        verify(notificationParser).parse(captor.capture(), eq(Transaction.class));
        assertThat(captor.getValue().getSerialNumber()).isEqualTo("PUB_KEY_ID");
        assertThat(captor.getValue().getSignature()).isEqualTo("signature");
        assertThat(captor.getValue().getBody()).isEqualTo("{\"resource\":{}}");
    }

    private Transaction transaction() {
        Transaction transaction = new Transaction();
        transaction.setOutTradeNo("ORDER-1");
        transaction.setTransactionId("WECHAT-1");
        transaction.setSpAppid("wx-service-provider-app");
        transaction.setSpMchid("1900000001");
        transaction.setSubMchid("1900000002");
        transaction.setTradeState(Transaction.TradeStateEnum.SUCCESS);
        transaction.setSuccessTime("2026-09-22T10:00:00+08:00");
        TransactionAmount amount = new TransactionAmount();
        amount.setTotal(1234);
        transaction.setAmount(amount);
        return transaction;
    }

    private WechatPayProperties properties() {
        WechatPayProperties properties = new WechatPayProperties();
        properties.setMode("WECHAT_SERVICE_PROVIDER");
        WechatPayProperties.Wechat wechat = properties.getWechat();
        wechat.setSpAppId("wx-service-provider-app");
        wechat.setSpMchid("1900000001");
        wechat.setNotifyUrl("https://api.example.com/api/payments/wechat/notify");
        return properties;
    }
}
