package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.common.exception.BizException;
import cn.studykid.growthplanet.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WxWechatClient 单元测试（纯逻辑，无 Spring 上下文）。
 * 校验：成功解析 openid、微信 errcode 转 E001、空凭证转 E503、网络异常转 E503。
 */
class WxWechatClientTest {

    static class StubRestTemplate extends RestTemplate {
        String body;
        RuntimeException error;

        @Override
        public <T> T getForObject(URI url, Class<T> clazz) {
            if (error != null) {
                throw error;
            }
            return (T) body;
        }
    }

    @Test
    void code2SessionReturnsOpenidOnSuccess() {
        var stub = new StubRestTemplate();
        stub.body = "{\"openid\":\"oABC\",\"session_key\":\"sk\",\"unionid\":\"u1\"}";
        var client = new WxWechatClient(stub, "appid", "secret");
        var session = client.code2Session("code123");
        assertEquals("oABC", session.getOpenid());
        assertEquals("u1", session.getUnionid());
    }

    @Test
    void code2SessionThrowsE001WhenWechatReturnsErrcode() {
        var stub = new StubRestTemplate();
        stub.body = "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
        var client = new WxWechatClient(stub, "appid", "secret");
        BizException ex = assertThrows(BizException.class, () -> client.code2Session("bad"));
        assertEquals(ResultCode.E001_NO_WX_AUTH, ex.getResultCode());
    }

    @Test
    void code2SessionThrowsE503OnBlankCredentials() {
        var stub = new StubRestTemplate();
        var client = new WxWechatClient(stub, "", "  ");
        BizException ex = assertThrows(BizException.class, () -> client.code2Session("code"));
        assertEquals(ResultCode.E503_UNAVAILABLE, ex.getResultCode());
    }

    @Test
    void code2SessionThrowsE503OnTransportFailure() {
        var stub = new StubRestTemplate();
        stub.error = new RestClientException("timeout");
        var client = new WxWechatClient(stub, "appid", "secret");
        BizException ex = assertThrows(BizException.class, () -> client.code2Session("code"));
        assertEquals(ResultCode.E503_UNAVAILABLE, ex.getResultCode());
    }
}
