package com.gechuang.stationery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.mainflow.MainFlowService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthControllerMerchantPhonePasswordTest {

    private static final String MOBILE = "13800138000";
    private static final String PASSWORD = "store-password";

    private final MainFlowService mainFlowService = mock(MainFlowService.class);
    private final MerchantLoginCodeService loginCodeService = mock(MerchantLoginCodeService.class);
    private final AuthController controller = new AuthController(mainFlowService, loginCodeService);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void oneMatchingStoreReturnsItsSessionAndHttpOnlyCookie() {
        when(mainFlowService.merchantStoresByMobileAndPassword(MOBILE, PASSWORD))
                .thenReturn(List.of(store("17", "东街文具店")));
        when(mainFlowService.loginMerchantByMobileAndPassword(MOBILE, 17L, PASSWORD))
                .thenReturn(new MainFlowService.AuthResult("session-token", Map.of("id", "17")));

        RestResponse<Map<String, Object>> result = controller.merchantMobilePasswordLogin(
                Map.of("mobile", MOBILE, "password", PASSWORD), response);

        assertThat(result.getData()).containsEntry("token", "session-token");
        ArgumentCaptor<Cookie> cookie = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookie.capture());
        assertThat(cookie.getValue().getName()).isEqualTo(MainFlowService.MERCHANT_TOKEN);
        assertThat(cookie.getValue().getValue()).isEqualTo("session-token");
        assertThat(cookie.getValue().isHttpOnly()).isTrue();
        verify(loginCodeService, never()).createPasswordStoreSelectionToken(MOBILE);
    }

    @Test
    void severalMatchingStoresReturnOnlyPasswordMatchesAndASelectionChallenge() {
        List<Map<String, Object>> matchingStores = List.of(store("17", "东街文具店"), store("18", "西街文具店"));
        when(mainFlowService.merchantStoresByMobileAndPassword(MOBILE, PASSWORD)).thenReturn(matchingStores);
        when(loginCodeService.createPasswordStoreSelectionToken(MOBILE)).thenReturn("selection-token");

        RestResponse<Map<String, Object>> result = controller.merchantMobilePasswordLogin(
                Map.of("mobile", MOBILE, "password", PASSWORD), response);

        assertThat(result.getData()).containsEntry("requiresStoreSelection", true)
                .containsEntry("selectionToken", "selection-token")
                .containsEntry("stores", matchingStores);
        verify(mainFlowService, never()).loginMerchantByMobileAndPassword(any(), any(), any());
    }

    @Test
    void selectingAStoreAfterPhonePasswordLoginRevalidatesThePassword() {
        when(loginCodeService.consumeStoreSelectionChallenge("selection-token"))
                .thenReturn(new MerchantLoginCodeService.StoreSelectionChallenge(MOBILE, "PASSWORD"));
        when(mainFlowService.loginMerchantByMobileAndPassword(MOBILE, 17L, PASSWORD))
                .thenReturn(new MainFlowService.AuthResult("session-token", Map.of("id", "17")));

        RestResponse<Map<String, Object>> result = controller.selectMerchantMobileLoginStore(
                Map.of("selectionToken", "selection-token", "storeId", "17", "password", PASSWORD), response);

        assertThat(result.getData()).containsEntry("token", "session-token");
        verify(mainFlowService).loginMerchantByMobileAndPassword(MOBILE, 17L, PASSWORD);
    }

    private Map<String, Object> store(String id, String shopName) {
        return Map.of("id", id, "account", "store_" + id, "shopName", shopName);
    }
}
