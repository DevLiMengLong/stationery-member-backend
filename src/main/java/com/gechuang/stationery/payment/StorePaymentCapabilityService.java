package com.gechuang.stationery.payment;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.mainflow.MainFlowMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StorePaymentCapabilityService {

    private static final String AUTHORIZATION_ENABLED = "ENABLED";
    private static final String AUTHORIZATION_PENDING = "PENDING";

    private final MainFlowMapper mapper;
    private final WechatPayProperties properties;

    public StorePaymentCapabilityService(MainFlowMapper mapper, WechatPayProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public PaymentCapability capability(Long storeId) {
        Map<String, Object> config = mapper.selectStoreWechatPayConfig(storeId);
        return capability(config);
    }

    public RealPaymentSettings requireRealPaymentSettings(Long storeId) {
        Map<String, Object> config = mapper.selectStoreWechatPayConfig(storeId);
        PaymentCapability capability = capability(config);
        if (!capability.realPaymentAvailable()) {
            throw new BusinessException(ErrorCode.BIZ_409, "当前门店仅支持记账模式");
        }
        return new RealPaymentSettings(
                properties.getWechat().getSpAppId(),
                properties.getWechat().getSpMchid(),
                textOrDefault(config.get("subMchid"), null));
    }

    private PaymentCapability capability(Map<String, Object> config) {
        PlatformPaymentMode platformMode = properties.platformMode();
        PaymentMode storeMode = parseStoreMode(config == null ? null : config.get("paymentMode"));
        String authorizationStatus = textOrDefault(
                config == null ? null : config.get("authorizationStatus"), AUTHORIZATION_PENDING);
        String subMchid = config == null ? null : textOrDefault(config.get("subMchid"), null);

        boolean realPaymentAvailable = platformMode == PlatformPaymentMode.WECHAT_SERVICE_PROVIDER
                && properties.hasCompleteWechatConfiguration()
                && storeMode == PaymentMode.REAL
                && StringUtils.hasText(subMchid)
                && AUTHORIZATION_ENABLED.equals(authorizationStatus);
        return new PaymentCapability(
                platformMode,
                storeMode,
                realPaymentAvailable ? PaymentMode.REAL : PaymentMode.BOOKKEEPING,
                authorizationStatus,
                realPaymentAvailable);
    }

    private PaymentMode parseStoreMode(Object value) {
        try {
            return PaymentMode.valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return PaymentMode.BOOKKEEPING;
        }
    }

    private String textOrDefault(Object value, String defaultValue) {
        String text = value == null ? null : String.valueOf(value).trim().toUpperCase();
        return StringUtils.hasText(text) ? text : defaultValue;
    }
}
