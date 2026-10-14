package cn.studykid.growthplanet.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WechatTokenProvider 单元测试：校验 access_token 获取成功、TTL 内缓存复用（只打一次微信）、微信错误抛 IllegalStateException。
 */
class WechatTokenProviderTest {

    static class StubRestTemplate extends RestTemplate {
        String body;
        int calls;
        RuntimeException error;

        @Override
        public <T> T getForObject(URI url, Class<T> clazz) {
            calls++;
            if (error != null) {
                throw error;
            }
            return (T) body;
        }
    }

    @Test
    void cachesTokenWithinTtl() {
        var stub = new StubRestTemplate();
        stub.body = "{\"access_token\":\"TOK\",\"expires_in\":7200}";
        var provider = new WechatTokenProvider(stub, "appid", "secret");
        assertEquals("TOK", provider.getAccessToken());
        assertEquals("TOK", provider.getAccessToken());
        assertEquals(1, stub.calls);
    }

    @Test
    void throwsOnWechatError() {
        var stub = new StubRestTemplate();
        stub.body = "{\"errcode\":40013,\"errmsg\":\"invalid appid\"}";
        var provider = new WechatTokenProvider(stub, "appid", "secret");
        assertThrows(IllegalStateException.class, provider::getAccessToken);
    }
}
