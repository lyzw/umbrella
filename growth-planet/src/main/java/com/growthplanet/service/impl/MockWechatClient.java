package com.growthplanet.service.impl;

import com.growthplanet.service.WechatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 开发/测试微信客户端桩：openid 由 code 派生，便于多用户测试；不发起真实网络请求。
 * 在 dev / test profile 启用。
 */
@Service
@Profile({"dev", "test"})
public class MockWechatClient implements WechatClient {

    @Override
    public WxSession code2Session(String code) {
        WxSession session = new WxSession();
        // openid 由 code 派生，保证不同 code -> 不同用户
        session.setOpenid("mock_openid_" + (code == null ? "null" : code));
        session.setUnionid("mock_unionid_" + (code == null ? "null" : code));
        session.setSessionKey("mock-session-key");
        return session;
    }
}
