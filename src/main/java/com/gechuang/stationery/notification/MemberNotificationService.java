package com.gechuang.stationery.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class MemberNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberNotificationService.class);

    private final MemberNotificationMessageFactory messageFactory;
    private final MemberNotificationSender sender;
    private final MemberNotificationProperties properties;
    private final SmsQuotaService smsQuotaService;

    public MemberNotificationService(MemberNotificationMessageFactory messageFactory,
                                     MemberNotificationSender sender,
                                     MemberNotificationProperties properties,
                                     SmsQuotaService smsQuotaService) {
        this.messageFactory = messageFactory;
        this.sender = sender;
        this.properties = properties;
        this.smsQuotaService = smsQuotaService;
    }

    public void notifyMemberCreated(Map<String, Object> store, Map<String, Object> member) {
        Long storeId = longValue(value(member, "storeId", "store_id"));
        if (storeId == null) {
            storeId = longValue(value(store, "storeId", "store_id", "id"));
        }
        send(storeId, messageFactory.memberCreated(
                text(value(store, "shopName", "storeName")),
                text(value(member, "mobile")),
                text(value(member, "memberNo", "memberCode", "member_code")),
                text(value(member, "wechatOpenid", "openId", "openid"))
        ));
    }

    public void notifyMemberCreated(String storeName, String mobile) {
        notifyMemberCreated(null, storeName, null, mobile);
    }

    public void notifyMemberCreated(String storeName, String memberNo, String mobile) {
        notifyMemberCreated(null, storeName, memberNo, mobile);
    }

    public void notifyMemberCreated(Long storeId, String storeName, String memberNo, String mobile) {
        send(storeId, messageFactory.memberCreated(storeName, mobile, memberNo, null));
    }

    public void notifyRechargeSucceeded(Map<String, Object> transaction) {
        send(longValue(value(transaction, "storeId", "store_id")), messageFactory.rechargeSucceeded(
                text(value(transaction, "storeName", "shopName")),
                text(value(transaction, "mobile")),
                text(value(transaction, "wechatOpenid", "openId", "openid")),
                decimal(value(transaction, "amount")),
                decimal(value(transaction, "giftAmount", "gift_amount")),
                decimal(value(transaction, "afterTotalBalance", "after_total_balance"))
        ));
    }

    public void notifyConsumptionSucceeded(Map<String, Object> transaction) {
        send(longValue(value(transaction, "storeId", "store_id")), messageFactory.consumptionSucceeded(
                text(value(transaction, "storeName", "shopName")),
                text(value(transaction, "mobile")),
                text(value(transaction, "wechatOpenid", "openId", "openid")),
                decimal(value(transaction, "amount")),
                decimal(value(transaction, "afterTotalBalance", "after_total_balance"))
        ));
    }

    private void send(Long storeId, MemberNotificationMessage message) {
        if (!properties.isEnabled()) {
            return;
        }
        MemberNotificationMessage deliverableMessage = withConfiguredOpenid(withStoreId(message, storeId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendSafely(storeId, deliverableMessage);
                }
            });
            return;
        }
        sendSafely(storeId, deliverableMessage);
    }

    private void sendSafely(Long storeId, MemberNotificationMessage message) {
        boolean reserved = false;
        if (isSmsChannel()) {
            try {
                reserved = smsQuotaService.reserve(storeId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not reserve SMS quota for store [{}]: {}", storeId, exception.getMessage());
                LOGGER.debug("SMS quota reservation failure detail", exception);
                return;
            }
            if (!reserved) {
                LOGGER.info("Skip SMS notification [{}] because store [{}] has no available SMS quota",
                        message.type(), storeId);
                return;
            }
        }

        boolean sent;
        try {
            sent = sender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.warn("Member notification [{}] failed for mobile [{}]: {}",
                    message.type(), message.recipientMobile(), exception.getMessage());
            LOGGER.debug("Member notification failure detail", exception);
            sent = false;
        }
        if (reserved && !sent) {
            try {
                smsQuotaService.release(storeId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not release SMS quota for store [{}]: {}", storeId, exception.getMessage());
                LOGGER.debug("SMS quota release failure detail", exception);
            }
        }
    }

    private boolean isSmsChannel() {
        String channel = properties.getChannel();
        return "tencent-cloud-sms".equalsIgnoreCase(channel) || "aliyun-sms".equalsIgnoreCase(channel);
    }

    private MemberNotificationMessage withStoreId(MemberNotificationMessage message, Long storeId) {
        return new MemberNotificationMessage(
                message.type(),
                message.recipientMobile(),
                message.recipientOpenid(),
                message.content(),
                message.templateData(),
                message.smsTemplateParams(),
                storeId
        );
    }

    private MemberNotificationMessage withConfiguredOpenid(MemberNotificationMessage message) {
        if (StringUtils.hasText(message.recipientOpenid()) || !StringUtils.hasText(message.recipientMobile())) {
            return message;
        }
        String openid = properties.getWechat().getOpenidByMobile().get(message.recipientMobile());
        if (!StringUtils.hasText(openid)) {
            return message;
        }
        return new MemberNotificationMessage(
                message.type(),
                message.recipientMobile(),
                openid,
                message.content(),
                message.templateData(),
                message.smsTemplateParams(),
                message.storeId()
        );
    }

    private Object value(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private String text(Object value) {
        String result = value == null ? null : String.valueOf(value).trim();
        return StringUtils.hasText(result) ? result : null;
    }

    private Long longValue(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private BigDecimal decimal(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return BigDecimal.ZERO.setScale(2);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }
}
