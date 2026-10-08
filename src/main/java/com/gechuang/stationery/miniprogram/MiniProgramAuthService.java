package com.gechuang.stationery.miniprogram;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.mainflow.MainFlowMapper;
import com.gechuang.stationery.mainflow.MainFlowService;
import java.util.LinkedHashMap;
import java.util.List;
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
        Long storeId = longValue(store.get("id"));
        Long bindingId = longValue(store.get("bindingId"));
        return authPayload(mainFlowService.loginMerchantByStoreId(storeId, bindingId), bindingId);
    }

    public List<Map<String, Object>> listWechatBindings(Long storeId) {
        return mapper.selectStoreWechatBindings(storeId).stream()
                .map(this::bindingView)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bindWechat(Long storeId, String code) {
        WechatCodeSessionClient.CodeSession codeSession = codeSessionClient.exchange(
                required(code, "微信登录 code 不能为空"));
        Map<String, Object> existing = mapper.selectStoreByWechatOpenid(codeSession.openid());
        if (existing != null && !existing.isEmpty()) {
            if (!Objects.equals(storeId, longValue(existing.get("id")))) {
                throw new BusinessException(ErrorCode.BIZ_409, "该微信账号已绑定其他门店");
            }
            return bindingView(existing);
        }
        try {
            mapper.insertStoreWechatBinding(storeId, codeSession.openid());
        } catch (DuplicateKeyException exception) {
            Map<String, Object> raced = mapper.selectStoreByWechatOpenid(codeSession.openid());
            if (raced != null && Objects.equals(storeId, longValue(raced.get("id")))) {
                return bindingView(raced);
            }
            throw new BusinessException(ErrorCode.BIZ_409, "该微信账号已绑定其他门店");
        }
        Map<String, Object> created = mapper.selectStoreByWechatOpenid(codeSession.openid());
        if (created == null || created.isEmpty()) {
            throw new BusinessException(ErrorCode.BIZ_409, "微信绑定未生效，请刷新后重试");
        }
        return bindingView(created);
    }

    public void unbindWechat(Long storeId, Long bindingId) {
        if (mapper.deleteStoreWechatBinding(storeId, bindingId) != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_404, "微信绑定不存在");
        }
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
        } else {
            try {
                mapper.insertStoreWechatBinding(storeId, codeSession.openid());
            } catch (DuplicateKeyException exception) {
                existing = mapper.selectStoreByWechatOpenid(codeSession.openid());
                if (existing == null || !Objects.equals(storeId, longValue(existing.get("id")))) {
                    throw new BusinessException(ErrorCode.BIZ_409, "该微信账号已绑定其他门店");
                }
            }
            if (existing == null || existing.isEmpty()) {
                existing = mapper.selectStoreByWechatOpenid(codeSession.openid());
            }
        }
        if (existing == null || existing.isEmpty()) {
            throw new BusinessException(ErrorCode.BIZ_409, "微信绑定未生效，请刷新后重试");
        }
        Long bindingId = longValue(existing.get("bindingId"));
        mapper.deleteSession(authResult.token());
        MainFlowService.AuthResult boundSession = mainFlowService.loginMerchantByStoreId(storeId, bindingId);
        return authPayload(boundSession, bindingId);
    }

    private Map<String, Object> authPayload(MainFlowService.AuthResult authResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bindingRequired", false);
        payload.put("token", authResult.token());
        payload.put("user", authResult.user());
        payload.put("expiresIn", MainFlowService.TOKEN_MAX_AGE_SECONDS);
        return payload;
    }

    private Map<String, Object> authPayload(MainFlowService.AuthResult authResult, Long bindingId) {
        Map<String, Object> payload = authPayload(authResult);
        payload.put("wechatBindingId", String.valueOf(bindingId));
        return payload;
    }

    private Map<String, Object> bindingView(Map<String, Object> row) {
        Map<String, Object> binding = new LinkedHashMap<>();
        Object bindingId = row.get("bindingId");
        Object label = row.get("wechatLabel");
        Object createdAt = row.get("bindingCreatedAt");
        if (bindingId == null) bindingId = row.get("id");
        if (label == null) label = row.get("label");
        if (createdAt == null) createdAt = row.get("createdAt");
        binding.put("id", String.valueOf(bindingId));
        binding.put("label", String.valueOf(label));
        binding.put("createdAt", createdAt);
        return binding;
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
