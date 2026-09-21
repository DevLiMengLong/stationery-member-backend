package com.gechuang.stationery.miniprogram;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.mainflow.MainFlowMapper;
import com.gechuang.stationery.mainflow.MainFlowService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MiniProgramAuthService {

    private final MainFlowMapper mapper;
    private final MainFlowService mainFlowService;
    private final WechatCodeSessionClient codeSessionClient;

    public MiniProgramAuthService(MainFlowMapper mapper,
                                  MainFlowService mainFlowService,
                                  WechatCodeSessionClient codeSessionClient) {
        this.mapper = mapper;
        this.mainFlowService = mainFlowService;
        this.codeSessionClient = codeSessionClient;
    }

    public Map<String, Object> codeLogin(String code) {
        WechatCodeSessionClient.CodeSession codeSession = codeSessionClient.exchange(required(code, "微信登录 code 不能为空"));
        Map<String, Object> store = mapper.selectStoreByWechatOpenid(codeSession.openid());
        if (store == null || store.isEmpty()) {
            return Map.of("bindingRequired", true);
        }
        return authPayload(mainFlowService.loginMerchantByStoreId(longValue(store.get("id"))));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bind(String code, String account, String password) {
        WechatCodeSessionClient.CodeSession codeSession = codeSessionClient.exchange(required(code, "微信登录 code 不能为空"));
        MainFlowService.AuthResult authResult = mainFlowService.loginMerchant(
                required(account, "商家账号不能为空"), required(password, "商家密码不能为空"));
        Long storeId = longValue(authResult.user().get("id"));
        Map<String, Object> existing = mapper.selectStoreByWechatOpenid(codeSession.openid());
        if (existing != null && !existing.isEmpty()) {
            if (!Objects.equals(storeId, longValue(existing.get("id")))) {
                throw new BusinessException(ErrorCode.BIZ_409, "该微信账号已绑定其他门店");
            }
            return authPayload(authResult);
        }
        try {
            mapper.insertStoreWechatBinding(storeId, codeSession.openid());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BIZ_409, "该微信账号已绑定其他门店");
        }
        return authPayload(authResult);
    }

    private Map<String, Object> authPayload(MainFlowService.AuthResult authResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bindingRequired", false);
        payload.put("token", authResult.token());
        payload.put("user", authResult.user());
        payload.put("expiresIn", MainFlowService.TOKEN_MAX_AGE_SECONDS);
        return payload;
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_422, message);
        }
        return value.trim();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
