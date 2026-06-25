package com.gechuang.stationery.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MemberNotificationMessageFactory {

    private static final String DEFAULT_STORE_NAME = "文具店";

    public MemberNotificationMessage memberCreated(String storeName, String mobile, String openid) {
        String normalizedStoreName = storeName(storeName);
        String content = normalizedStoreName + "，欢迎您加入成为会员，充值更优惠，消费更明白";
        Map<String, String> data = new LinkedHashMap<>();
        data.put("first", "欢迎您加入成为会员");
        data.put("keyword1", normalizedStoreName);
        data.put("remark", "充值更优惠，消费更明白");
        return new MemberNotificationMessage(
                MemberNotificationType.MEMBER_CREATED,
                mobile,
                openid,
                content,
                data,
                List.of(normalizedStoreName)
        );
    }

    public MemberNotificationMessage rechargeSucceeded(String storeName, String mobile, String openid,
                                                       BigDecimal amount, BigDecimal giftAmount,
                                                       BigDecimal balance) {
        String normalizedStoreName = storeName(storeName);
        String suffix = mobileSuffix(mobile);
        String suffixLabel = suffixLabel(suffix);
        String amountText = money(amount);
        String giftText = money(giftAmount);
        String balanceText = money(balance);
        String content = normalizedStoreName + "，" + suffixLabel + "，您成功充值" + amountText
                + "元，赠送" + giftText + "元，账户余额" + balanceText + "元，门店消费优先扣除充值金额";
        Map<String, String> data = new LinkedHashMap<>();
        data.put("first", normalizedStoreName + "充值成功通知");
        data.put("keyword1", suffixLabel);
        data.put("keyword2", "充值" + amountText + "元");
        data.put("keyword3", "赠送" + giftText + "元");
        data.put("keyword4", "账户余额" + balanceText + "元");
        data.put("remark", "门店消费优先扣除充值金额");
        return new MemberNotificationMessage(
                MemberNotificationType.RECHARGE_SUCCEEDED,
                mobile,
                openid,
                content,
                data,
                List.of(normalizedStoreName, suffix, amountText, giftText, balanceText)
        );
    }

    public MemberNotificationMessage consumptionSucceeded(String storeName, String mobile, String openid,
                                                          BigDecimal amount, BigDecimal balance) {
        String normalizedStoreName = storeName(storeName);
        String suffix = mobileSuffix(mobile);
        String suffixLabel = suffixLabel(suffix);
        String amountText = money(amount);
        String balanceText = money(balance);
        String content = normalizedStoreName + "，" + suffixLabel + "，您成功消费" + amountText
                + "元，账户余额" + balanceText + "元，充值消费更优惠";
        Map<String, String> data = new LinkedHashMap<>();
        data.put("first", normalizedStoreName + "消费成功通知");
        data.put("keyword1", suffixLabel);
        data.put("keyword2", "消费" + amountText + "元");
        data.put("keyword3", "账户余额" + balanceText + "元");
        data.put("remark", "充值消费更优惠");
        return new MemberNotificationMessage(
                MemberNotificationType.CONSUMPTION_SUCCEEDED,
                mobile,
                openid,
                content,
                data,
                List.of(normalizedStoreName, suffix, amountText, balanceText)
        );
    }

    private String storeName(String storeName) {
        return StringUtils.hasText(storeName) ? storeName.trim() : DEFAULT_STORE_NAME;
    }

    private String mobileSuffix(String mobile) {
        String value = StringUtils.hasText(mobile) ? mobile.trim() : "";
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }

    private String suffixLabel(String suffix) {
        return "尾号" + suffix + "会员";
    }

    private String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
