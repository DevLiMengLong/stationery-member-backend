package com.gechuang.stationery.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.mockito.ArgumentCaptor;

class AliyunSmsNotificationSenderTest {

    @Test
    void shouldBeConstructibleBySpringWithAliyunChannel() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context, "stationery.notification.channel=aliyun-sms");
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(MemberNotificationProperties.class, () -> new MemberNotificationProperties());
            context.registerBean(AliyunSmsNotificationSender.class);

            context.refresh();

            assertThat(context.getBean(AliyunSmsNotificationSender.class)).isNotNull();
        }
    }

    @Test
    void shouldSendRechargeSmsWithAliyunTemplateParams() throws Exception {
        MemberNotificationProperties properties = new MemberNotificationProperties();
        properties.getAliyunSms().setSignName("智墨云软件");
        properties.getAliyunSms().setRechargeTemplateCode("SMS_512281100");
        Client client = mock(Client.class);
        SendSmsResponse response = new SendSmsResponse()
                .setBody(new SendSmsResponseBody().setCode("OK").setMessage("OK"));
        when(client.sendSmsWithOptions(any(SendSmsRequest.class), any(RuntimeOptions.class)))
                .thenReturn(response);

        AliyunSmsNotificationSender sender = new AliyunSmsNotificationSender(
                properties, new ObjectMapper(), client);
        sender.send(new MemberNotificationMessage(
                MemberNotificationType.RECHARGE_SUCCEEDED,
                "13800000073",
                null,
                "充值成功",
                java.util.Map.of(),
                java.util.List.of("晨光文具店", "100", "10")
        ));

        ArgumentCaptor<SendSmsRequest> requestCaptor = ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(client).sendSmsWithOptions(requestCaptor.capture(), any(RuntimeOptions.class));
        SendSmsRequest request = requestCaptor.getValue();
        assertThat(request.getPhoneNumbers()).isEqualTo("13800000073");
        assertThat(request.getSignName()).isEqualTo("智墨云软件");
        assertThat(request.getTemplateCode()).isEqualTo("SMS_512281100");
        JsonNode params = new ObjectMapper().readTree(request.getTemplateParam());
        assertThat(params.get("business_code").asText()).isEqualTo("晨光文具店");
        assertThat(params.get("input").asText()).isEqualTo("100");
        assertThat(params.get("input_gift").asText()).isEqualTo("10");
    }

    @Test
    void shouldFailWhenAliyunReturnsNonOkCode() throws Exception {
        MemberNotificationProperties properties = new MemberNotificationProperties();
        properties.getAliyunSms().setSignName("智墨云软件");
        properties.getAliyunSms().setConsumptionTemplateCode("SMS_512231098");
        Client client = mock(Client.class);
        SendSmsResponse response = new SendSmsResponse()
                .setBody(new SendSmsResponseBody().setCode("isv.BUSINESS_LIMIT_CONTROL")
                        .setMessage("触发流控"));
        when(client.sendSmsWithOptions(any(SendSmsRequest.class), any(RuntimeOptions.class)))
                .thenReturn(response);

        AliyunSmsNotificationSender sender = new AliyunSmsNotificationSender(
                properties, new ObjectMapper(), client);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sender.send(new MemberNotificationMessage(
                MemberNotificationType.CONSUMPTION_SUCCEEDED,
                "13800000073",
                null,
                "消费成功",
                java.util.Map.of(),
                java.util.List.of("晨光文具店", "10", "100")
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("触发流控");
    }

    @Test
    void shouldOmitTemplateParamWhenTemplateHasNoVariables() throws Exception {
        MemberNotificationProperties properties = new MemberNotificationProperties();
        properties.getAliyunSms().setSignName("智墨云软件");
        properties.getAliyunSms().setRechargeTemplateCode("SMS_512281100");
        properties.getAliyunSms().setRechargeParamNames("");
        Client client = mock(Client.class);
        when(client.sendSmsWithOptions(any(SendSmsRequest.class), any(RuntimeOptions.class)))
                .thenReturn(new SendSmsResponse()
                        .setBody(new SendSmsResponseBody().setCode("OK").setMessage("OK")));

        AliyunSmsNotificationSender sender = new AliyunSmsNotificationSender(
                properties, new ObjectMapper(), client);
        sender.send(new MemberNotificationMessage(
                MemberNotificationType.RECHARGE_SUCCEEDED,
                "13800000073",
                null,
                "充值成功",
                java.util.Map.of(),
                java.util.List.of("晨光文具店", "100", "10")
        ));

        ArgumentCaptor<SendSmsRequest> requestCaptor = ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(client).sendSmsWithOptions(requestCaptor.capture(), any(RuntimeOptions.class));
        assertThat(requestCaptor.getValue().getTemplateParam()).isNull();
    }
}
