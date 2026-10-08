package com.gechuang.stationery.miniprogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.mainflow.MainFlowMapper;
import com.gechuang.stationery.mainflow.MainFlowService;
import java.util.LinkedHashMap;
import java.util.List;
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
        when(mainFlowService.loginMerchantByStoreId(1L, 51L)).thenReturn(authResult(1L));

        Map<String, Object> result = service.codeLogin("valid-code");

        assertThat(result)
                .containsEntry("bindingRequired", false)
                .containsEntry("token", "token-1")
                .containsEntry("wechatBindingId", "51")
                .containsEntry("expiresIn", MainFlowService.TOKEN_MAX_AGE_SECONDS)
                .containsKey("user")
                .doesNotContainKeys("openid", "sessionKey");
        verify(mainFlowService).loginMerchantByStoreId(1L, 51L);
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
        when(mainFlowService.loginMerchantByStoreId(1L, 51L))
                .thenReturn(new MainFlowService.AuthResult("wechat-token", authResult(1L).user()));
        when(mapper.selectStoreByWechatOpenid("openid-new")).thenReturn(null, binding(1L, 51L));

        Map<String, Object> result = service.bind("bind-code", "store-account", "store-password");

        verify(mapper).insertStoreWechatBinding(1L, "openid-new");
        verify(mapper).deleteSession("token-1");
        assertThat(result)
                .containsEntry("bindingRequired", false)
                .containsEntry("token", "wechat-token")
                .containsEntry("wechatBindingId", "51")
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
        when(mainFlowService.loginMerchantByStoreId(1L, 51L))
                .thenReturn(new MainFlowService.AuthResult("wechat-token", authResult(1L).user()));

        Map<String, Object> result = service.bind("retry-code", "store-account", "store-password");

        verify(mapper, never()).insertStoreWechatBinding(1L, "openid-bound");
        verify(mapper).deleteSession("token-1");
        assertThat(result).containsEntry("token", "wechat-token").containsEntry("wechatBindingId", "51");
    }

    @Test
    void disabledBoundStoreCannotObtainANewSession() {
        when(codeSessionClient.exchange("disabled-code"))
                .thenReturn(new WechatCodeSessionClient.CodeSession("openid-disabled", "session-key"));
        when(mapper.selectStoreByWechatOpenid("openid-disabled")).thenReturn(store(3L, "DISABLED"));
        when(mainFlowService.loginMerchantByStoreId(3L, 53L))
                .thenThrow(new BusinessException(ErrorCode.AUTH_423, ErrorCode.AUTH_423.getDefaultMessage()));

        assertThatThrownBy(() -> service.codeLogin("disabled-code"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_423));
    }

    @Test
    void missingWechatSecretFailsBeforeAnyNetworkRequest() {
        MiniProgramProperties properties = new MiniProgramProperties();
        properties.setAppId("public-app-id");
        HttpWechatCodeSessionClient client = new HttpWechatCodeSessionClient(
                properties, RestClient.builder(), new ObjectMapper());

        assertThatThrownBy(() -> client.exchange("code"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BIZ_409));
    }

    @Test
    void bindingListReturnsOnlySafeDisplayFieldsAndStringIds() {
        when(mapper.selectStoreWechatBindings(1L)).thenReturn(List.of(Map.of(
                "bindingId", 9007199254740993L,
                "label", "微信账号（尾号 1234）",
                "createdAt", "2026-09-30 10:00:00",
                "openid", "must-not-leak")));

        List<Map<String, Object>> result = service.listWechatBindings(1L);

        assertThat(result).containsExactly(Map.of(
                "id", "9007199254740993",
                "label", "微信账号（尾号 1234）",
                "createdAt", "2026-09-30 10:00:00"));
    }

    @Test
    void authenticatedWechatBindingUsesServerResolvedOpenidAndIsIdempotentForThisStore() {
        when(codeSessionClient.exchange("binding-code"))
                .thenReturn(new WechatCodeSessionClient.CodeSession("openid-1234", "session-key"));
        when(mapper.selectStoreByWechatOpenid("openid-1234"))
                .thenReturn(null, binding(1L, 71L));

        Map<String, Object> result = service.bindWechat(1L, "binding-code");

        verify(mapper).insertStoreWechatBinding(1L, "openid-1234");
        assertThat(result).containsEntry("id", "71")
                .containsEntry("label", "微信账号（尾号 1234）")
                .doesNotContainKeys("openid", "sessionKey");
    }

    @Test
    void authenticatedWechatBindingRejectsAnOpenidOwnedByAnotherStore() {
        when(codeSessionClient.exchange("duplicate-code"))
                .thenReturn(new WechatCodeSessionClient.CodeSession("openid-bound", "session-key"));
        when(mapper.selectStoreByWechatOpenid("openid-bound")).thenReturn(binding(2L, 72L));

        assertThatThrownBy(() -> service.bindWechat(1L, "duplicate-code"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BIZ_409));
        verify(mapper, never()).insertStoreWechatBinding(1L, "openid-bound");
    }

    @Test
    void unbindingIsScopedToTheCurrentStoreAndUnknownLinksAreNotFound() {
        when(mapper.deleteStoreWechatBinding(1L, 51L)).thenReturn(1);
        when(mapper.deleteStoreWechatBinding(1L, 99L)).thenReturn(0);

        service.unbindWechat(1L, 51L);

        verify(mapper).deleteStoreWechatBinding(1L, 51L);
        assertThatThrownBy(() -> service.unbindWechat(1L, 99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_404));
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
        store.put("bindingId", id + 50);
        store.put("status", status);
        return store;
    }

    private Map<String, Object> binding(Long storeId, Long bindingId) {
        Map<String, Object> row = store(storeId, "ACTIVE");
        row.put("bindingId", bindingId);
        row.put("wechatLabel", "微信账号（尾号 1234）");
        row.put("bindingCreatedAt", "2026-09-30 10:00:00");
        return row;
    }
}
