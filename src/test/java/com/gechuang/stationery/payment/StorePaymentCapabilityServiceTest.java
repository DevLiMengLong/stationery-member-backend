package com.gechuang.stationery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.mainflow.MainFlowMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StorePaymentCapabilityServiceTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final WechatPayProperties properties = new WechatPayProperties();
    private final StorePaymentCapabilityService service = new StorePaymentCapabilityService(mapper, properties);

    @Test
    void missingStoreConfigDefaultsToBookkeeping() {
        when(mapper.selectStoreWechatPayConfig(1L)).thenReturn(null);

        PaymentCapability result = service.capability(1L);

        assertThat(result.platformMode()).isEqualTo(PlatformPaymentMode.BOOKKEEPING);
        assertThat(result.configuredStoreMode()).isEqualTo(PaymentMode.BOOKKEEPING);
        assertThat(result.effectiveMode()).isEqualTo(PaymentMode.BOOKKEEPING);
        assertThat(result.realPaymentAvailable()).isFalse();
    }

    @Test
    void partialPlatformCredentialsCannotEnableRealPayment() {
        properties.setMode("WECHAT_SERVICE_PROVIDER");
        properties.getWechat().setSpMchid("1900000001");
        when(mapper.selectStoreWechatPayConfig(1L)).thenReturn(realStore("ENABLED"));

        PaymentCapability result = service.capability(1L);

        assertThat(result.platformMode()).isEqualTo(PlatformPaymentMode.WECHAT_SERVICE_PROVIDER);
        assertThat(result.configuredStoreMode()).isEqualTo(PaymentMode.REAL);
        assertThat(result.effectiveMode()).isEqualTo(PaymentMode.BOOKKEEPING);
        assertThat(result.realPaymentAvailable()).isFalse();
    }

    @Test
    void pendingStoreAuthorizationCannotEnableRealPayment() {
        enableCompletePlatformConfiguration();
        when(mapper.selectStoreWechatPayConfig(1L)).thenReturn(realStore("PENDING"));

        PaymentCapability result = service.capability(1L);

        assertThat(result.authorizationStatus()).isEqualTo("PENDING");
        assertThat(result.effectiveMode()).isEqualTo(PaymentMode.BOOKKEEPING);
        assertThat(result.realPaymentAvailable()).isFalse();
    }

    @Test
    void completePlatformAndEnabledStoreExposeRealPayment() {
        enableCompletePlatformConfiguration();
        when(mapper.selectStoreWechatPayConfig(1L)).thenReturn(realStore("ENABLED"));

        PaymentCapability result = service.capability(1L);

        assertThat(result.platformMode()).isEqualTo(PlatformPaymentMode.WECHAT_SERVICE_PROVIDER);
        assertThat(result.configuredStoreMode()).isEqualTo(PaymentMode.REAL);
        assertThat(result.effectiveMode()).isEqualTo(PaymentMode.REAL);
        assertThat(result.authorizationStatus()).isEqualTo("ENABLED");
        assertThat(result.realPaymentAvailable()).isTrue();
    }

    @Test
    void malformedPlatformModeSafelyFallsBackToBookkeeping() {
        properties.setMode("REAL_NOW");
        when(mapper.selectStoreWechatPayConfig(1L)).thenReturn(realStore("ENABLED"));

        PaymentCapability result = service.capability(1L);

        assertThat(result.platformMode()).isEqualTo(PlatformPaymentMode.BOOKKEEPING);
        assertThat(result.effectiveMode()).isEqualTo(PaymentMode.BOOKKEEPING);
        assertThat(result.realPaymentAvailable()).isFalse();
    }

    @Test
    void malformedStoreModeSafelyFallsBackToBookkeeping() {
        enableCompletePlatformConfiguration();
        when(mapper.selectStoreWechatPayConfig(1L)).thenReturn(Map.of(
                "paymentMode", "UNKNOWN",
                "subMchid", "1900000002",
                "authorizationStatus", "ENABLED"));

        PaymentCapability result = service.capability(1L);

        assertThat(result.configuredStoreMode()).isEqualTo(PaymentMode.BOOKKEEPING);
        assertThat(result.effectiveMode()).isEqualTo(PaymentMode.BOOKKEEPING);
    }

    private Map<String, Object> realStore(String authorizationStatus) {
        return Map.of(
                "paymentMode", "REAL",
                "subMchid", "1900000002",
                "authorizationStatus", authorizationStatus);
    }

    private void enableCompletePlatformConfiguration() {
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
    }
}
