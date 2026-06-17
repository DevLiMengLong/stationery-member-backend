package com.gechuang.stationery.auth;

import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import com.gechuang.stationery.common.RestResponse;
import com.gechuang.stationery.mainflow.MainFlowService;
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

    private final MainFlowService mainFlowService;

    public AuthController(MainFlowService mainFlowService) {
        this.mainFlowService = mainFlowService;
    }

    @PostMapping("/merchant/login")
    public RestResponse<Map<String, Object>> merchantLogin(@RequestBody Map<String, Object> body,
                                                           HttpServletResponse response) {
        MainFlowService.AuthResult result = mainFlowService.loginMerchant(text(body.get("account")), text(body.get("password")));
        response.addCookie(tokenCookie(MainFlowService.MERCHANT_TOKEN, result.token(), MainFlowService.TOKEN_MAX_AGE_SECONDS));
        return RestResponse.success(authPayload(result));
    }

    @GetMapping("/merchant/me")
    public RestResponse<Map<String, Object>> merchantMe(HttpServletRequest request) {
        return RestResponse.success(mainFlowService.requireMerchant(request));
    }

    @PostMapping("/merchant/logout")
    public RestResponse<Void> merchantLogout(HttpServletRequest request, HttpServletResponse response) {
        mainFlowService.logout(request, MainFlowService.MERCHANT_TOKEN);
        response.addCookie(tokenCookie(MainFlowService.MERCHANT_TOKEN, "", 0));
        return RestResponse.success();
    }

    @PostMapping("/admin/login")
    public RestResponse<Map<String, Object>> adminLogin(@RequestBody Map<String, Object> body,
                                                        HttpServletResponse response) {
        MainFlowService.AuthResult result = mainFlowService.loginAdmin(text(body.get("username")), text(body.get("password")));
        response.addCookie(tokenCookie(MainFlowService.ADMIN_TOKEN, result.token(), MainFlowService.TOKEN_MAX_AGE_SECONDS));
        return RestResponse.success(authPayload(result));
    }

    @GetMapping("/admin/me")
    public RestResponse<Map<String, Object>> adminMe(HttpServletRequest request) {
        return RestResponse.success(mainFlowService.requireAdmin(request));
    }

    @PostMapping("/admin/logout")
    public RestResponse<Void> adminLogout(HttpServletRequest request, HttpServletResponse response) {
        mainFlowService.logout(request, MainFlowService.ADMIN_TOKEN);
        response.addCookie(tokenCookie(MainFlowService.ADMIN_TOKEN, "", 0));
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

    private Map<String, Object> authPayload(MainFlowService.AuthResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", result.token());
        payload.put("user", result.user());
        payload.put("expiresIn", MainFlowService.TOKEN_MAX_AGE_SECONDS);
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
