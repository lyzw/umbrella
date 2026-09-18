package cn.studykid.growthplanet.service;

import lombok.Data;

/**
 * 微信客户端抽象：code2Session 换取 openid 等会话信息。
 * dev/test 使用 {@code MockWechatClient}，prod 使用 {@code WxWechatClient}。
 */
public interface WechatClient {

    /** 用登录 code 换取微信会话（openid 等）。 */
    WxSession code2Session(String code);

    /** 微信会话信息值对象。 */
    @Data
    class WxSession {
        private String openid;
        private String unionid;
        private String sessionKey;
    }
}
