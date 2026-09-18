package com.growthplanet.service.impl;

import com.growthplanet.service.WechatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 生产微信客户端：调用 code2Session 接口。仅在 prod profile 启用。
 */
@Service
@Profile("prod")
public class WxWechatClient implements WechatClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${wx.appid:}")
    private String appid;

    @Value("${wx.secret:}")
    private String secret;

    @Override
    public WxSession code2Session(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + appid
                + "&secret=" + secret
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        ResponseEntity<WxSession> resp = restTemplate.getForEntity(url, WxSession.class);
        WxSession session = resp.getBody();
        if (session == null || session.getOpenid() == null) {
            throw new RuntimeException("微信 code2Session 失败");
        }
        return session;
    }
}
