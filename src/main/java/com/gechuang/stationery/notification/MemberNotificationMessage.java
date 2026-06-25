package com.gechuang.stationery.notification;

import java.util.List;
import java.util.Map;

public record MemberNotificationMessage(
        MemberNotificationType type,
        String recipientMobile,
        String recipientOpenid,
        String content,
        Map<String, String> templateData,
        List<String> smsTemplateParams
) {
    public MemberNotificationMessage(MemberNotificationType type,
                                     String recipientMobile,
                                     String recipientOpenid,
                                     String content,
                                     Map<String, String> templateData) {
        this(type, recipientMobile, recipientOpenid, content, templateData, List.of());
    }

    public MemberNotificationMessage {
        templateData = templateData == null ? Map.of() : Map.copyOf(templateData);
        smsTemplateParams = smsTemplateParams == null ? List.of() : List.copyOf(smsTemplateParams);
    }
}
