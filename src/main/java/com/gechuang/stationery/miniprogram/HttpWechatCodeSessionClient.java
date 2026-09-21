package com.gechuang.stationery.miniprogram;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gechuang.stationery.common.BusinessException;
import com.gechuang.stationery.common.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpWechatCodeSessionClient implements WechatCodeSessionClient {

    private static final String CODE_SESSION_PATH = "/sns/jscode2session";

    private final MiniProgramProperties properties;
    private final RestClient restClient;

    public HttpWechatCodeSessionClient(MiniProgramProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl("https://api.weixin.qq.com").build();
    }

    @Override
    public CodeSession exchange(String code) {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            throw new BusinessException(ErrorCode.BIZ_409, "微信小程序登录尚未配置");
        }
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_422, "微信登录 code 不能为空");
        }

        CodeSessionResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(CODE_SESSION_PATH)
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .queryParam("js_code", code.trim())
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(CodeSessionResponse.class);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.SYS_500, "微信登录服务暂时不可用");
        }

        if (response == null) {
            throw new BusinessException(ErrorCode.SYS_500, "微信登录服务响应为空");
        }
        if (response.errcode() != null && response.errcode() != 0) {
            throw new BusinessException(ErrorCode.AUTH_401, "微信登录凭证无效或已过期");
        }
        if (!StringUtils.hasText(response.openid())) {
            throw new BusinessException(ErrorCode.SYS_500, "微信登录服务未返回用户标识");
        }
        return new CodeSession(response.openid(), response.sessionKey());
    }

    private record CodeSessionResponse(
            String openid,
            @JsonProperty("session_key") String sessionKey,
            Integer errcode,
            String errmsg) {
    }
}
