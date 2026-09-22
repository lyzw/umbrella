package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.service.WechatClient;
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

    private final RestTemplate restTemplate;

    public WxWechatClient() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        restTemplate = new RestTemplate(factory);
    }

    @Value("${wx.appid:}")
    private String appid;

    @Value("${wx.secret:}")
    private String secret;

    @Override
    public WxSession code2Session(String code) {
        if (appid.isBlank() || secret.isBlank()) {
            throw new BizException(
                    ResultCode.E503_UNAVAILABLE);
        }
        var uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", "{appid}").queryParam("secret", "{secret}")
                .queryParam("js_code", "{code}").queryParam("grant_type", "authorization_code")
                .buildAndExpand(appid, secret, code).encode().toUri();
        try {
            WxSession session = restTemplate.getForObject(uri, WxSession.class);
            if (session == null || session.getOpenid() == null || session.getOpenid().isBlank()) {
                throw new BizException(
                        ResultCode.E001_NO_WX_AUTH);
            }
            return session;
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new BizException(
                    ResultCode.E503_UNAVAILABLE);
        }
    }
}
