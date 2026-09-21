package com.gechuang.stationery.miniprogram;

public interface WechatCodeSessionClient {

    CodeSession exchange(String code);

    record CodeSession(String openid, String sessionKey) {
    }
}
