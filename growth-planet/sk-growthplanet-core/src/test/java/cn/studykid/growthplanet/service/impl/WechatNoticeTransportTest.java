package cn.studykid.growthplanet.service.impl;

import cn.studykid.growthplanet.config.NoticeProperties;
import cn.studykid.growthplanet.service.NoticeTransport;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WechatNoticeTransport 单元测试：校验 errcode→Outcome 映射、缺 data 模板判 REJECTED、网络失败判 RETRYABLE。
 */
class WechatNoticeTransportTest {

    static class StubTokenProvider extends WechatTokenProvider {
        StubTokenProvider(RestTemplate rt) {
            super(rt, "appid", "secret");
        }

        @Override
        String getAccessToken() {
            return "TK";
        }
    }

    static class StubRestTemplate extends RestTemplate {
        String body = "{}";
        int calls;
        RuntimeException error;

        @Override
        public <T> T getForObject(URI url, Class<T> clazz) {
            return (T) "{}";
        }

        @Override
        public <T> T postForObject(URI url, Object request, Class<T> responseType) {
            calls++;
            if (error != null) {
                throw error;
            }
            return (T) body;
        }
    }

    private NoticeTransport transportWith(String dataTemplate) {
        var settings = new NoticeProperties();
        if (dataTemplate != null) {
            settings.getDataTemplates().put("menu", dataTemplate);
        }
        return new WechatNoticeTransport(new StubTokenProvider(new StubRestTemplate()), new StubRestTemplate(), settings);
    }

    @Test
    void acceptedWhenErrcodeZero() {
        var rt = new StubRestTemplate();
        rt.body = "{\"errcode\":0}";
        var settings = new NoticeProperties();
        settings.getDataTemplates().put("menu", "{\"thing1\":{\"value\":\"{eventKey}\"}}");
        var t = new WechatNoticeTransport(new StubTokenProvider(rt), rt, settings);
        assertEquals(NoticeTransport.Outcome.ACCEPTED, t.send("o1", "tpl", "menu", "k1"));
    }

    @Test
    void rejectedOnTemplateError() {
        var rt = new StubRestTemplate();
        rt.body = "{\"errcode\":40003}";
        var settings = new NoticeProperties();
        settings.getDataTemplates().put("menu", "{}");
        var t = new WechatNoticeTransport(new StubTokenProvider(rt), rt, settings);
        assertEquals(NoticeTransport.Outcome.REJECTED, t.send("o1", "tpl", "menu", "k1"));
    }

    @Test
    void rejectedWhenNoDataTemplate() {
        var t = transportWith(null);
        assertEquals(NoticeTransport.Outcome.REJECTED, t.send("o1", "tpl", "menu", "k1"));
    }

    @Test
    void retryableOnTransportFailure() {
        var rt = new StubRestTemplate();
        rt.error = new RestClientException("x");
        var settings = new NoticeProperties();
        settings.getDataTemplates().put("menu", "{}");
        var t = new WechatNoticeTransport(new StubTokenProvider(rt), rt, settings);
        assertEquals(NoticeTransport.Outcome.RETRYABLE_FAILURE, t.send("o1", "tpl", "menu", "k1"));
    }
}
