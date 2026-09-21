package com.gechuang.stationery.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.payment.WechatPayProperties;
import com.gechuang.stationery.common.BusinessException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.partnerpayments.nativepay.NativePayService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WechatGatewayConfigurationTest {

    @Test
    void missingCredentialsSelectDisabledGatewayWithoutCreatingSdkClients() {
        WechatPayProperties properties = new WechatPayProperties();
        WechatGatewayConfiguration.SdkClientFactory factory = mock(
                WechatGatewayConfiguration.SdkClientFactory.class);

        context(properties, factory).run(context -> {
            assertThat(context).hasSingleBean(WechatPaymentGateway.class);
            assertThat(context.getBean(WechatPaymentGateway.class))
                    .isInstanceOf(DisabledWechatPaymentGateway.class);
            assertThatThrownBy(() -> context.getBean(WechatPaymentGateway.class)
                    .queryByOutTradeNo("ORDER-1", "1900000002"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("微信真实支付尚未配置");
            verifyNoInteractions(factory);
        });
    }

    @Test
    void completeServiceProviderConfigurationSelectsNativeGatewayWithInjectedSdkClients() {
        WechatPayProperties properties = completeProperties();
        WechatGatewayConfiguration.SdkClientFactory factory = mock(
                WechatGatewayConfiguration.SdkClientFactory.class);
        NativePayService nativePayService = mock(NativePayService.class);
        NotificationParser notificationParser = mock(NotificationParser.class);
        when(factory.create(properties)).thenReturn(
                new WechatGatewayConfiguration.SdkClients(nativePayService, notificationParser));

        context(properties, factory).run(context -> {
            assertThat(context).hasSingleBean(WechatPaymentGateway.class);
            assertThat(context.getBean(WechatPaymentGateway.class))
                    .isInstanceOf(ServiceProviderNativePaymentGateway.class);
        });
    }

    @Test
    void malformedCredentialFilesFallBackToDisabledGatewayAtStartup() {
        WechatPayProperties properties = completeProperties();
        WechatGatewayConfiguration.SdkClientFactory factory = mock(
                WechatGatewayConfiguration.SdkClientFactory.class);
        when(factory.create(properties)).thenThrow(new IllegalArgumentException("invalid private key"));

        context(properties, factory).run(context -> {
            assertThat(context).hasSingleBean(WechatPaymentGateway.class);
            assertThat(context.getBean(WechatPaymentGateway.class))
                    .isInstanceOf(DisabledWechatPaymentGateway.class);
        });
    }

    private ApplicationContextRunner context(WechatPayProperties properties,
                                             WechatGatewayConfiguration.SdkClientFactory factory) {
        return new ApplicationContextRunner()
                .withBean(WechatPayProperties.class, () -> properties)
                .withBean(WechatGatewayConfiguration.SdkClientFactory.class, () -> factory)
                .withUserConfiguration(WechatGatewayConfiguration.class);
    }

    private WechatPayProperties completeProperties() {
        WechatPayProperties properties = new WechatPayProperties();
        properties.setMode("WECHAT_SERVICE_PROVIDER");
        WechatPayProperties.Wechat wechat = properties.getWechat();
        wechat.setSpAppId("wx-service-provider-app");
        wechat.setSpMchid("1900000001");
        wechat.setMerchantSerialNumber("serial-number");
        wechat.setPrivateKeyPath("/secure/private-key.pem");
        wechat.setApiV3Key("01234567890123456789012345678901");
        wechat.setPublicKeyId("PUB_KEY_ID_0111111111");
        wechat.setPublicKeyPath("/secure/wechatpay-public-key.pem");
        wechat.setNotifyUrl("https://api.example.com/api/payments/wechat/notify");
        return properties;
    }
}
