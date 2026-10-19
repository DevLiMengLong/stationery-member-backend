package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.notification.MemberNotificationService;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MerchantPhoneLoginTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));

    @Test
    void newStoreBindsAllProvidedLoginMobiles() {
        when(mapper.selectStoreByAccount("new_store")).thenReturn(null);
        when(mapper.insertStore(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> store = invocation.getArgument(0);
            store.put("id", 12L);
            return 1;
        });
        when(mapper.selectStoreById(12L)).thenReturn(store(12L));

        service.createStore(Map.of("account", "new_store", "contactMobile", "13800138000",
                "loginMobiles", List.of("13800138000", "13900139000")));

        verify(mapper).insertStoreLoginMobiles(eq(12L), eq(List.of("13800138000", "13900139000")));
    }

    @Test
    void theSamePhoneCanBeBoundToMultipleStoreAccounts() {
        when(mapper.selectStoreByAccount("first_store")).thenReturn(null);
        when(mapper.selectStoreByAccount("second_store")).thenReturn(null);
        when(mapper.insertStore(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> store = invocation.getArgument(0);
            store.put("id", "first_store".equals(store.get("account")) ? 21L : 22L);
            return 1;
        });
        when(mapper.selectStoreById(21L)).thenReturn(store(21L));
        when(mapper.selectStoreById(22L)).thenReturn(store(22L));

        service.createStore(Map.of("account", "first_store", "contactMobile", "13800138000"));
        service.createStore(Map.of("account", "second_store", "contactMobile", "13800138000"));

        verify(mapper).insertStoreLoginMobiles(21L, List.of("13800138000"));
        verify(mapper).insertStoreLoginMobiles(22L, List.of("13800138000"));
    }

    @Test
    void merchantPhoneSessionIsCreatedOnlyForTheChosenStoreAndItsBoundMobile() {
        when(mapper.selectStoreByLoginMobile(eq("13800138000"), eq(9L), any(LocalDateTime.class)))
                .thenReturn(store(9L));
        when(mapper.selectStoreById(9L)).thenReturn(store(9L));

        MainFlowService.AuthResult result = service.loginMerchantByMobile("13800138000", 9L);

        assertThat(result.token()).isNotBlank();
        assertThat(result.user()).containsEntry("id", "9").doesNotContainKey("passwordHash");
        verify(mapper).updateStoreLastLogin(9L);
        verify(mapper).insertSession(eq(result.token()), eq("MERCHANT"), eq(9L), any(LocalDateTime.class));
    }

    @Test
    void selectingAStoreNotBoundToTheVerifiedPhoneDoesNotCreateASession() {
        when(mapper.selectStoreByLoginMobile(eq("13800138000"), eq(10L), any(LocalDateTime.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.loginMerchantByMobile("13800138000", 10L))
                .isInstanceOf(BusinessException.class);

        verify(mapper, never()).insertSession(any(), any(), any(), any());
    }

    @Test
    void phonePasswordLookupReturnsOnlyEligibleStoresMatchedByTheSubmittedPassword() {
        List<Map<String, Object>> matches = List.of(
                Map.of("id", "9", "account", "store_9", "shopName", "东街文具店"));
        when(mapper.selectStoresByLoginMobileAndPassword(eq("13800138000"), any(), any(LocalDateTime.class)))
                .thenReturn(matches);

        List<Map<String, Object>> result = service.merchantStoresByMobileAndPassword("13800138000", "secret");

        assertThat(result).isEqualTo(matches);
        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(mapper).selectStoresByLoginMobileAndPassword(eq("13800138000"), passwordHash.capture(),
                any(LocalDateTime.class));
        assertThat(passwordHash.getValue()).isNotEqualTo("secret");
    }

    @Test
    void selectingAStoreForPhonePasswordLoginRechecksItsPasswordBeforeCreatingASession() throws Exception {
        Map<String, Object> selected = store(9L);
        selected.put("passwordHash", hashPassword("correct"));
        when(mapper.selectStoreByLoginMobile(eq("13800138000"), eq(9L), any(LocalDateTime.class)))
                .thenReturn(selected);
        when(mapper.selectStoreById(9L)).thenReturn(store(9L));

        assertThatThrownBy(() -> service.loginMerchantByMobileAndPassword("13800138000", 9L, "wrong"))
                .isInstanceOf(com.gechuang.stationery.common.BusinessException.class);
        verify(mapper, never()).insertSession(any(), any(), any(), any());

        MainFlowService.AuthResult result =
                service.loginMerchantByMobileAndPassword("13800138000", 9L, "correct");
        assertThat(result.token()).isNotBlank();
        verify(mapper).updateStoreLastLogin(9L);
        verify(mapper).insertSession(eq(result.token()), eq("MERCHANT"), eq(9L), any(LocalDateTime.class));
    }

    @Test
    void storeRejectsDuplicateOrMalformedLoginMobiles() {
        when(mapper.selectStoreByAccount("new_store")).thenReturn(null);

        assertThatThrownBy(() -> service.createStore(Map.of("account", "new_store",
                "loginMobiles", List.of("13800138000", "13800138000"))))
                .isInstanceOf(BusinessException.class);

        verify(mapper, never()).insertStore(anyMap());
        verify(mapper, never()).insertStoreLoginMobiles(any(), anyList());
    }

    @Test
    void storeCannotSilentlyBindTheSharedDefaultPhoneWhenNoLoginPhoneWasProvided() {
        when(mapper.selectStoreByAccount("new_store")).thenReturn(null);
        when(mapper.insertStore(anyMap())).thenAnswer(invocation -> {
            Map<String, Object> store = invocation.getArgument(0);
            store.put("id", 13L);
            return 1;
        });
        when(mapper.selectStoreById(13L)).thenReturn(store(13L));

        service.createStore(Map.of("account", "new_store"));

        verify(mapper).insertStore(anyMap());
        verify(mapper, never()).insertStoreLoginMobiles(any(), anyList());
    }

    private Map<String, Object> store(Long id) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("account", "store_" + id);
        value.put("passwordHash", "hash");
        value.put("shopName", "测试门店" + id);
        value.put("contactMobile", "13800138000");
        value.put("status", "ACTIVE");
        value.put("expiresAt", null);
        return value;
    }

    private String hashPassword(String password) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte value : digest) hash.append(String.format("%02x", value));
        return hash.toString();
    }
}
