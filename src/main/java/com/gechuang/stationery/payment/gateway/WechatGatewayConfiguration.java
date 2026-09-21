package com.gechuang.stationery.payment.gateway;

import com.gechuang.stationery.payment.PlatformPaymentMode;
import com.gechuang.stationery.payment.WechatPayProperties;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.partnerpayments.nativepay.NativePayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WechatGatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WechatGatewayConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    SdkClientFactory wechatSdkClientFactory() {
        return properties -> {
            WechatPayProperties.Wechat wechat = properties.getWechat();
            RSAPublicKeyConfig config = new RSAPublicKeyConfig.Builder()
                    .merchantId(wechat.getSpMchid())
                    .privateKeyFromPath(wechat.getPrivateKeyPath())
                    .merchantSerialNumber(wechat.getMerchantSerialNumber())
                    .apiV3Key(wechat.getApiV3Key())
                    .publicKeyFromPath(wechat.getPublicKeyPath())
                    .publicKeyId(wechat.getPublicKeyId())
                    .build();
            return new SdkClients(
                    new NativePayService.Builder().config(config).build(),
                    new NotificationParser(config));
        };
    }

    @Bean
    WechatPaymentGateway wechatPaymentGateway(WechatPayProperties properties, SdkClientFactory factory) {
        if (properties.platformMode() != PlatformPaymentMode.WECHAT_SERVICE_PROVIDER
                || !properties.hasCompleteWechatConfiguration()) {
            return new DisabledWechatPaymentGateway();
        }
        try {
            SdkClients clients = factory.create(properties);
            return new ServiceProviderNativePaymentGateway(
                    clients.nativePayService(), clients.notificationParser(), properties);
        } catch (RuntimeException exception) {
            log.error("WeChat payment gateway configuration is invalid; real payment stays disabled: {}",
                    exception.getClass().getSimpleName());
            return new DisabledWechatPaymentGateway();
        }
    }

    interface SdkClientFactory {
        SdkClients create(WechatPayProperties properties);
    }

    record SdkClients(NativePayService nativePayService, NotificationParser notificationParser) {
    }
}
