package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.notification.MemberNotificationService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class StoreAccountExpiryTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));

    @Test
    void expiredMerchantCannotLoginAndReceivesRenewalMessage() {
        when(mapper.selectStoreByAccount("expired_store")).thenReturn(store("2000-01-01 00:00:00"));

        assertThatThrownBy(() -> service.loginMerchant("expired_store", "password"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_423);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                            .isEqualTo("账号已过期，请联系管理员续期");
                });

        verify(mapper, never()).updateStoreLastLogin(7L);
    }

    @Test
    void expiredMerchantSessionCannotAccessBusinessApis() {
        when(mapper.selectStoreByToken(eq("merchant-token"), any(LocalDateTime.class)))
                .thenReturn(store("2000-01-01 00:00:00"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(MainFlowService.MERCHANT_TOKEN, "merchant-token"));

        assertThatThrownBy(() -> service.requireMerchant(request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_423);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                            .isEqualTo("账号已过期，请联系管理员续期");
                });
    }

    @Test
    void createStorePersistsTheConfiguredExpiration() {
        when(mapper.selectStoreByAccount("new_store")).thenReturn(null);
        when(mapper.insertStore(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> saved = invocation.getArgument(0);
            saved.put("id", 8L);
            return 1;
        });
        when(mapper.selectStoreById(8L)).thenReturn(store(null));

        service.createStore(Map.of("account", "new_store", "expiresAt", "2026-10-31T23:59"));

        ArgumentCaptor<Map<String, Object>> savedStore = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertStore(savedStore.capture());
        assertThat(savedStore.getValue().get("expiresAt"))
                .isEqualTo(LocalDateTime.of(2026, 10, 31, 23, 59));
    }

    @Test
    void updateStoreCanClearExpiration() {
        Map<String, Object> existing = store("2026-10-31 23:59:00");
        when(mapper.selectStoreById(7L)).thenReturn(existing);

        service.updateStore(7L, Map.of("expiresAt", ""));

        ArgumentCaptor<Map<String, Object>> updatedStore = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateStore(updatedStore.capture());
        assertThat(updatedStore.getValue()).containsKey("expiresAt");
        assertThat(updatedStore.getValue().get("expiresAt")).isNull();
    }

    @Test
    void accountWithoutExpirationRemainsUsableForExistingStores() {
        Map<String, Object> existing = store(null);
        when(mapper.selectStoreByAccount("expired_store")).thenReturn(existing);
        when(mapper.selectStoreById(7L)).thenReturn(existing);

        MainFlowService.AuthResult result = service.loginMerchant("expired_store", "password");

        assertThat(result.token()).isNotBlank();
        verify(mapper).updateStoreLastLogin(7L);
    }

    @Test
    void updateStorePreservesExpirationWhenOlderClientsOmitTheField() {
        when(mapper.selectStoreById(7L)).thenReturn(store("2026-10-31 23:59:00"));

        service.updateStore(7L, Map.of("shopName", "修改后的门店"));

        ArgumentCaptor<Map<String, Object>> updatedStore = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateStore(updatedStore.capture());
        assertThat(updatedStore.getValue().get("expiresAt"))
                .isEqualTo(LocalDateTime.of(2026, 10, 31, 23, 59));
    }

    @Test
    void createStoreRejectsMalformedExpiration() {
        when(mapper.selectStoreByAccount("new_store")).thenReturn(null);

        assertThatThrownBy(() -> service.createStore(Map.of(
                "account", "new_store", "expiresAt", "not-a-date")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.VALIDATION_422);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                            .isEqualTo("账号有效期格式不正确");
                });
    }

    private Map<String, Object> store(String expiresAt) {
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("id", 7L);
        store.put("account", "expired_store");
        store.put("passwordHash", "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8");
        store.put("status", "ACTIVE");
        store.put("expiresAt", expiresAt);
        return store;
    }
}
