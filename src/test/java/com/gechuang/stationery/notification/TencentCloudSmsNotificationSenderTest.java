package com.gechuang.stationery.notification;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TencentCloudSmsNotificationSenderTest {

    @Test
    void shouldSendSmsWithTencentCloudTemplateParams() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MemberNotificationProperties properties = new MemberNotificationProperties();
        properties.getTencentSms().setSecretId("secret-id");
        properties.getTencentSms().setSecretKey("secret-key");
        properties.getTencentSms().setSmsSdkAppId("1400000000");
        properties.getTencentSms().setSignName("晨光文具店");
        properties.getTencentSms().setRechargeTemplateId("1002");
        TencentCloudSmsNotificationSender sender = new TencentCloudSmsNotificationSender(
                builder,
                properties,
                new ObjectMapper()
        );
        MemberNotificationMessage message = new MemberNotificationMessage(
                MemberNotificationType.RECHARGE_SUCCEEDED,
                "13800000073",
                null,
                "晨光文具店，尾号0073会员，您成功充值100元，赠送10元，账户余额110元，门店消费优先扣除充值金额",
                Map.of(),
                List.of("晨光文具店", "0073", "100", "10", "110")
        );

        server.expect(once(), requestTo("https://sms.tencentcloudapi.com/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-TC-Action", "SendSms"))
                .andExpect(header("X-TC-Version", "2021-01-11"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.SmsSdkAppId").value("1400000000"))
                .andExpect(jsonPath("$.SignName").value("晨光文具店"))
                .andExpect(jsonPath("$.TemplateId").value("1002"))
                .andExpect(jsonPath("$.PhoneNumberSet[0]").value("+8613800000073"))
                .andExpect(jsonPath("$.TemplateParamSet[0]").value("晨光文具店"))
                .andExpect(jsonPath("$.TemplateParamSet[1]").value("0073"))
                .andExpect(jsonPath("$.TemplateParamSet[2]").value("100"))
                .andExpect(jsonPath("$.TemplateParamSet[3]").value("10"))
                .andExpect(jsonPath("$.TemplateParamSet[4]").value("110"))
                .andRespond(withSuccess("""
                        {"Response":{"SendStatusSet":[{"Code":"Ok"}],"RequestId":"request-id"}}
                        """, MediaType.APPLICATION_JSON));

        sender.send(message);

        server.verify();
    }
}
