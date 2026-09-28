package com.gechuang.stationery.miniprogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpWechatCodeSessionClientTest {

    @Test
    void textPlainWechatResponseIsParsedAsJson() {
        MiniProgramProperties properties = new MiniProgramProperties();
        properties.setAppId("public-app-id");
        properties.setAppSecret("server-side-secret");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session")))
                .andExpect(queryParam("appid", "public-app-id"))
                .andExpect(queryParam("secret", "server-side-secret"))
                .andExpect(queryParam("js_code", "valid-code"))
                .andExpect(queryParam("grant_type", "authorization_code"))
                .andRespond(withSuccess("""
                        {"openid":"openid-1","session_key":"session-key-1"}
                        """, MediaType.TEXT_PLAIN));
        HttpWechatCodeSessionClient client = new HttpWechatCodeSessionClient(
                properties, restClientBuilder, new ObjectMapper());

        WechatCodeSessionClient.CodeSession result = client.exchange("valid-code");

        assertThat(result).isEqualTo(new WechatCodeSessionClient.CodeSession("openid-1", "session-key-1"));
        server.verify();
    }
}
