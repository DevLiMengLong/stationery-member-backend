package com.gechuang.stationery.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MemberNotificationMessageFactoryTest {

    private final MemberNotificationMessageFactory factory = new MemberNotificationMessageFactory();

    @Test
    void memberCreatedMessageShouldWelcomeNewMember() {
        MemberNotificationMessage message = factory.memberCreated(
                "晨光文具店", "13800000073", "M202609190073", null);

        assertThat(message.type()).isEqualTo(MemberNotificationType.MEMBER_CREATED);
        assertThat(message.recipientMobile()).isEqualTo("13800000073");
        assertThat(message.content()).isEqualTo("晨光文具店，欢迎您加入成为会员，充值更优惠，消费更明白");
        assertThat(message.templateData())
                .containsEntry("first", "欢迎您加入成为会员")
                .containsEntry("keyword1", "晨光文具店")
                .containsEntry("remark", "充值更优惠，消费更明白");
        assertThat(message.smsTemplateParams()).containsExactly("晨光文具店", "M202609190073");
    }

    @Test
    void rechargeMessageShouldIncludeMobileSuffixAmountGiftAndBalance() {
        MemberNotificationMessage message = factory.rechargeSucceeded(
                "晨光文具店",
                "13800000073",
                null,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("110.00")
        );

        assertThat(message.type()).isEqualTo(MemberNotificationType.RECHARGE_SUCCEEDED);
        assertThat(message.content())
                .isEqualTo("晨光文具店，尾号0073会员，您成功充值100元，赠送10元，账户余额110元，门店消费优先扣除充值金额");
        assertThat(message.templateData())
                .containsEntry("keyword1", "尾号0073会员")
                .containsEntry("keyword2", "充值100元")
                .containsEntry("keyword3", "赠送10元")
                .containsEntry("keyword4", "账户余额110元")
                .containsEntry("remark", "门店消费优先扣除充值金额");
        assertThat(message.smsTemplateParams()).containsExactly("晨光文具店", "100", "10");
    }

    @Test
    void consumptionMessageShouldIncludeMobileSuffixAmountAndBalance() {
        MemberNotificationMessage message = factory.consumptionSucceeded(
                "晨光文具店",
                "13800000073",
                null,
                new BigDecimal("10.00"),
                new BigDecimal("5.00")
        );

        assertThat(message.type()).isEqualTo(MemberNotificationType.CONSUMPTION_SUCCEEDED);
        assertThat(message.content())
                .isEqualTo("晨光文具店，尾号0073会员，您成功消费10元，账户余额5元，充值消费更优惠");
        assertThat(message.templateData())
                .containsEntry("keyword1", "尾号0073会员")
                .containsEntry("keyword2", "消费10元")
                .containsEntry("keyword3", "账户余额5元")
                .containsEntry("remark", "充值消费更优惠");
        assertThat(message.smsTemplateParams()).containsExactly("晨光文具店", "10", "5");
    }
}
