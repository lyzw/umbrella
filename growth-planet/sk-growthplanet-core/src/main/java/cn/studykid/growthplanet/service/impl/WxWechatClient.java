package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import cn.studykid.growthplanet.service.WechatClient;
import cn.studykid.growthplanet.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * 生产微信客户端：调用 code2Session 接口。仅在 prod profile 启用（dev/test 使用 {@code MockWechatClient}）。
 * 注意：客户端选择由 Spring profile 驱动，与 {@code wx.mock} 配置无关（该键为历史遗留死配置，已从 prod yml 移除）。
 */
@Slf4j
@Service
@Profile("prod")
public class WxWechatClient implements WechatClient {

    private final RestTemplate restTemplate;

    public WxWechatClient() {
        this(buildRestTemplate());
    }

    /** 测试/显式注入用构造器。 */
    WxWechatClient(RestTemplate restTemplate, String appid, String secret) {
        this.restTemplate = restTemplate;
        this.appid = appid;
        this.secret = secret;
    }

    private static RestTemplate buildRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    @Value("${wx.appid:}")
    private String appid;

    @Value("${wx.secret:}")
    private String secret;

    @Override
    public WxSession code2Session(String code) {
        if (appid.isBlank() || secret.isBlank()) {
            throw new BizException(ResultCode.E503_UNAVAILABLE);
        }
        var uri = UriComponentsBuilder
                .fromUriString("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", "{appid}").queryParam("secret", "{secret}")
                .queryParam("js_code", "{code}").queryParam("grant_type", "authorization_code")
                .buildAndExpand(appid, secret, code).encode().toUri();
        try {
            // 取原始响应体，避免 RestTemplate 把微信错误 JSON 直接映射到 WxSession 而吞掉 errcode。
            String body = restTemplate.getForObject(uri, String.class);
            WxSession session = parse(body);
            if (session == null || session.getOpenid() == null || session.getOpenid().isBlank()) {
                throw new BizException(ResultCode.E001_NO_WX_AUTH, "微信未返回 openid");
            }
            return session;
        } catch (BizException ex) {
            throw ex;
        } catch (RestClientException ex) {
            // 网络/超时：服务暂不可用，可重试。
            log.warn("wx_code2session_transport_failure");
            throw new BizException(ResultCode.E503_UNAVAILABLE);
        } catch (RuntimeException ex) {
            log.warn("wx_code2session_parse_failure");
            throw new BizException(ResultCode.E503_UNAVAILABLE);
        }
    }

    private WxSession parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        Map<String, Object> map = JsonUtils.fromJson(body, new TypeReference<Map<String, Object>>() {
        });
        Object errcode = map.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            // 微信校验失败（code 失效 / appid 或 secret 错误 / 配额等）；按未授权处理，不泄露具体原因。
            log.warn("wx_code2session_error errcode={}", errcode);
            throw new BizException(ResultCode.E001_NO_WX_AUTH, "微信登录校验失败");
        }
        WxSession session = new WxSession();
        Object openid = map.get("openid");
        Object unionid = map.get("unionid");
        Object sessionKey = map.get("session_key");
        session.setOpenid(openid == null ? null : String.valueOf(openid));
        session.setUnionid(unionid == null ? null : String.valueOf(unionid));
        session.setSessionKey(sessionKey == null ? null : String.valueOf(sessionKey));
        return session;
    }
}
