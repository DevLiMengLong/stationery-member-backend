package com.gechuang.stationery.mainflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.notification.MemberNotificationService;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class MainFlowBearerAuthenticationTest {

    private final MainFlowMapper mapper = mock(MainFlowMapper.class);
    private final MainFlowService service = new MainFlowService(mapper, mock(MemberNotificationService.class));

    @Test
    void merchantBearerTokenLoadsTheBoundStore() {
        MockHttpServletRequest request = requestWithAuthorization("Bearer merchant-token");
        when(mapper.selectStoreByToken(eq("merchant-token"), any())).thenReturn(activeStore());

        Map<String, Object> merchant = service.requireMerchant(request);

        assertThat(merchant).containsEntry("id", 1L).doesNotContainKey("passwordHash");
    }

    @Test
    void merchantCookieRemainsSupportedWhenAuthorizationIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(MainFlowService.MERCHANT_TOKEN, "cookie-token"));
        when(mapper.selectStoreByToken(eq("cookie-token"), any())).thenReturn(activeStore());

        assertThat(service.requireMerchant(request)).containsEntry("id", 1L);
    }

    @Test
    void blankBearerDoesNotFallBackToMerchantCookie() {
        MockHttpServletRequest request = requestWithAuthorization("Bearer   ");
        request.setCookies(new Cookie(MainFlowService.MERCHANT_TOKEN, "cookie-token"));

        assertThatThrownBy(() -> service.requireMerchant(request))
                .isInstanceOf(BusinessException.class);
        verify(mapper).selectStoreByToken(eq(""), any());
        verify(mapper, never()).selectStoreByToken(eq("cookie-token"), any());
    }

    @Test
    void merchantLogoutDeletesBearerSession() {
        MockHttpServletRequest request = requestWithAuthorization("Bearer merchant-token");

        service.logoutMerchant(request);

        verify(mapper).deleteSession("merchant-token");
    }

    @Test
    void adminAuthenticationDoesNotAcceptMerchantBearerToken() {
        MockHttpServletRequest request = requestWithAuthorization("Bearer merchant-token");

        assertThatThrownBy(() -> service.requireAdmin(request))
                .isInstanceOf(BusinessException.class);
        verify(mapper).selectAdminByToken(eq(""), any());
    }

    @Test
    void adminLogoutIgnoresBearerAndDeletesOnlyAdminCookieSession() {
        MockHttpServletRequest request = requestWithAuthorization("Bearer merchant-token");
        request.setCookies(new Cookie(MainFlowService.ADMIN_TOKEN, "admin-cookie-token"));

        service.logoutAdmin(request);

        verify(mapper).deleteSession("admin-cookie-token");
        verify(mapper, never()).deleteSession("merchant-token");
    }

    @Test
    void merchantSessionCanBeIssuedFromATrustedBoundStoreId() {
        when(mapper.selectStoreById(1L)).thenReturn(activeStore());

        MainFlowService.AuthResult result = service.loginMerchantByStoreId(1L);

        assertThat(result.token()).hasSize(32);
        assertThat(result.user()).containsEntry("id", 1L).doesNotContainKey("passwordHash");
        verify(mapper).updateStoreLastLogin(1L);
        verify(mapper).insertSession(eq(result.token()), eq("MERCHANT"), eq(1L), any());
    }

    private MockHttpServletRequest requestWithAuthorization(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, value);
        return request;
    }

    private Map<String, Object> activeStore() {
        Map<String, Object> store = new LinkedHashMap<>();
        store.put("id", 1L);
        store.put("status", "ACTIVE");
        store.put("passwordHash", "not-public");
        return store;
    }
}
