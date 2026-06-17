package com.gechuang.stationery.auth;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.demo.DemoDataStore;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int TOKEN_MAX_AGE_SECONDS = 12 * 60 * 60;
    private final DemoDataStore dataStore;

    public AuthController(DemoDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @PostMapping("/merchant/login")
    public RestResponse<Map<String, Object>> merchantLogin(@RequestBody Map<String, Object> body,
                                                           HttpServletResponse response) {
        DemoDataStore.AuthResult result = dataStore.loginMerchant(text(body.get("account")), text(body.get("password")));
        response.addCookie(tokenCookie(DemoDataStore.MERCHANT_TOKEN, result.token(), TOKEN_MAX_AGE_SECONDS));
        return RestResponse.success(authPayload(result));
    }

    @GetMapping("/merchant/me")
    public RestResponse<Map<String, Object>> merchantMe(HttpServletRequest request) {
        return RestResponse.success(dataStore.currentStoreMap(dataStore.requireMerchant(request)));
    }

    @PostMapping("/merchant/logout")
    public RestResponse<Void> merchantLogout(HttpServletRequest request, HttpServletResponse response) {
        dataStore.logout(dataStore.tokenFromCookie(request, DemoDataStore.MERCHANT_TOKEN));
        response.addCookie(tokenCookie(DemoDataStore.MERCHANT_TOKEN, "", 0));
        return RestResponse.success();
    }

    @PostMapping("/admin/login")
    public RestResponse<Map<String, Object>> adminLogin(@RequestBody Map<String, Object> body,
                                                        HttpServletResponse response) {
        DemoDataStore.AuthResult result = dataStore.loginAdmin(text(body.get("username")), text(body.get("password")));
        response.addCookie(tokenCookie(DemoDataStore.ADMIN_TOKEN, result.token(), TOKEN_MAX_AGE_SECONDS));
        return RestResponse.success(authPayload(result));
    }

    @GetMapping("/admin/me")
    public RestResponse<Map<String, Object>> adminMe(HttpServletRequest request) {
        return RestResponse.success(dataStore.currentAdminMap(dataStore.requireAdmin(request)));
    }

    @PostMapping("/admin/logout")
    public RestResponse<Void> adminLogout(HttpServletRequest request, HttpServletResponse response) {
        dataStore.logout(dataStore.tokenFromCookie(request, DemoDataStore.ADMIN_TOKEN));
        response.addCookie(tokenCookie(DemoDataStore.ADMIN_TOKEN, "", 0));
        return RestResponse.success();
    }

    @PostMapping("/password/sms-code")
    public RestResponse<Map<String, Object>> sendResetCode(@RequestBody Map<String, Object> body) {
        String mobile = text(body.get("mobile"));
        if (!StringUtils.hasText(mobile) || !mobile.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "手机号格式不正确");
        }
        return RestResponse.success(Map.of("mockCode", "123456", "expiresIn", 300));
    }

    @PostMapping("/password/reset")
    public RestResponse<Void> resetPassword(@RequestBody Map<String, Object> body) {
        if (!"123456".equals(text(body.get("code")))) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "验证码不正确");
        }
        return RestResponse.success();
    }

    private Map<String, Object> authPayload(DemoDataStore.AuthResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", result.token());
        payload.put("user", result.data());
        payload.put("expiresIn", TOKEN_MAX_AGE_SECONDS);
        return payload;
    }

    private Cookie tokenCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
