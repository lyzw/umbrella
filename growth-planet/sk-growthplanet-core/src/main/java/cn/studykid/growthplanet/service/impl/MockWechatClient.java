package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.service.WechatClient;
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
        if (code == null || code.isBlank() || code.length() > 51) {
            throw new BizException(
                    ResultCode.E400_INVALID_ARGUMENT, "演示 code 长度须为1至51");
        }
        WxSession session = new WxSession();
        // openid 由 code 派生，保证不同 code -> 不同用户
        session.setOpenid("mock_openid_" + (code == null ? "null" : code));
        session.setUnionid("mock_unionid_" + (code == null ? "null" : code));
        session.setSessionKey("mock-session-key");
        return session;
    }
}
