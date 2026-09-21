package com.gechuang.stationery.miniprogram;

import com.gechuang.stationery.common.RestResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mini/auth")
public class MiniProgramAuthController {

    private final MiniProgramAuthService authService;

    public MiniProgramAuthController(MiniProgramAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/code-login")
    public RestResponse<Map<String, Object>> codeLogin(@Valid @RequestBody CodeLoginRequest request) {
        return RestResponse.success(authService.codeLogin(request.code()));
    }

    @PostMapping("/bind")
    public RestResponse<Map<String, Object>> bind(@Valid @RequestBody BindRequest request) {
        return RestResponse.success(authService.bind(request.code(), request.account(), request.password()));
    }

    public record CodeLoginRequest(@NotBlank String code) {
    }

    public record BindRequest(@NotBlank String code,
                              @NotBlank String account,
                              @NotBlank String password) {
    }
}
