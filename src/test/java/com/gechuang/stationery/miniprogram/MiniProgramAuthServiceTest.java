package com.gechuang.stationery.miniprogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.mainflow.MainFlowMapper;
import com.gechuang.stationery.mainflow.MainFlowService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MiniProgramAuthServiceTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MainFlowService mainFlowService = mock(MainFlowService.class);
    private final WechatCodeSessionClient codeSessionClient = mock(WechatCodeSessionClient.class);
    private final MiniProgramAuthService service = new MiniProgramAuthService(
            mapper, mainFlowService, codeSessionClient);

    @Test
    void existingBindingReturnsAStandardMerchantSession() {
        when(codeSessionClient.exchange("valid-code"))
                .thenReturn(new WechatCodeSessionClient.CodeSession("openid-1", "session-key"));
        when(mapper.selectStoreByWechatOpenid("openid-1")).thenReturn(store(1L, "ACTIVE"));
        when(mainFlowService.loginMerchantByStoreId(1L)).thenReturn(authResult(1L));

        Map<String, Object> result = service.codeLogin("valid-code");

        assertThat(result)
                .containsEntry("bindingRequired", false)
                .containsEntry("token", "token-1")
                .containsEntry("expiresIn", MainFlowService.TOKEN_MAX_AGE_SECONDS)
                .containsKey("user")
                .doesNotContainKeys("openid", "sessionKey");
    }

    @Test
    void unboundOpenidRequestsBindingWithoutLeakingWechatIdentifiers() {
        when(codeSessionClient.exchange("unbound-code"))
                .thenReturn(new WechatCodeSessionClient.CodeSession("openid-new", "session-key"));
        when(mapper.selectStoreByWechatOpenid("openid-new")).thenReturn(null);

        Map<String, Object> result = service.codeLogin("unbound-code");

        assertThat(result)
                .containsOnly(Map.entry("bindingRequired", true))
                .doesNotContainKeys("openid", "sessionKey", "token");
        verify(mainFlowService, never()).loginMerchantByStoreId(1L);
    }

    @Test
    void validMerchantCredentialsBindTheServerResolvedOpenid() {
        when(codeSessionClient.exchange("bind-code"))
                .thenReturn(new WechatCodeSessionClient.CodeSession("openid-new", "session-key"));
        when(mainFlowService.loginMerchant("store-account", "store-password")).thenReturn(authResult(1L));
        when(mapper.selectStoreByWechatOpenid("openid-new")).thenReturn(null);

        Map<String, Object> result = service.bind("bind-code", "store-account", "store-password");

        verify(mapper).insertStoreWechatBinding(1L, "openid-new");
        assertThat(result)
                .containsEntry("bindingRequired", false)
                .containsEntry("token", "token-1")
                .doesNotContainKeys("openid", "sessionKey");
    }

    @Test
    void bindingOwnedByAnotherStoreIsRejectedAfterCredentialValidation() {
        when(codeSessionClient.exchange("duplicate-code"))
                .thenReturn(new WechatCodeSessionClient.CodeSession("openid-bound", "session-key"));
        when(mainFlowService.loginMerchant("store-account", "store-password")).thenReturn(authResult(1L));
        when(mapper.selectStoreByWechatOpenid("openid-bound")).thenReturn(store(2L, "ACTIVE"));

        assertThatThrownBy(() -> service.bind("duplicate-code", "store-account", "store-password"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BIZ_409));
        verify(mapper, never()).insertStoreWechatBinding(1L, "openid-bound");
    }

    @Test
    void retryingBindingForTheSameStoreIsIdempotent() {
        when(codeSessionClient.exchange("retry-code"))
                .thenReturn(new WechatCodeSessionClient.CodeSession("openid-bound", "session-key"));
        when(mainFlowService.loginMerchant("store-account", "store-password")).thenReturn(authResult(1L));
        when(mapper.selectStoreByWechatOpenid("openid-bound")).thenReturn(store(1L, "ACTIVE"));

        Map<String, Object> result = service.bind("retry-code", "store-account", "store-password");

        verify(mapper, never()).insertStoreWechatBinding(1L, "openid-bound");
        assertThat(result).containsEntry("token", "token-1");
    }

    @Test
    void disabledBoundStoreCannotObtainANewSession() {
        when(codeSessionClient.exchange("disabled-code"))
                .thenReturn(new WechatCodeSessionClient.CodeSession("openid-disabled", "session-key"));
        when(mapper.selectStoreByWechatOpenid("openid-disabled")).thenReturn(store(3L, "DISABLED"));
        when(mainFlowService.loginMerchantByStoreId(3L))
                .thenThrow(new BusinessException(ErrorCode.AUTH_423, ErrorCode.AUTH_423.getDefaultMessage()));

        assertThatThrownBy(() -> service.codeLogin("disabled-code"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_423));
    }

    @Test
    void missingWechatSecretFailsBeforeAnyNetworkRequest() {
        MiniProgramProperties properties = new MiniProgramProperties();
        properties.setAppId("public-app-id");
        HttpWechatCodeSessionClient client = new HttpWechatCodeSessionClient(properties, RestClient.builder());

        assertThatThrownBy(() -> client.exchange("code"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BIZ_409));
    }

    private MainFlowService.AuthResult authResult(Long storeId) {
        return new MainFlowService.AuthResult("token-" + storeId, Map.of(
                "id", storeId,
                "status", "ACTIVE",
                "shopName", "Store " + storeId));
    }

    private Map<String, Object> store(Long id, String status) {
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("id", id);
        store.put("status", status);
        return store;
    }
}
